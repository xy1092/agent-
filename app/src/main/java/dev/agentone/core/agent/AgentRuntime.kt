package dev.agentone.core.agent

import dev.agentone.core.database.AgentRunDao
import dev.agentone.core.database.AgentStepLogDao
import dev.agentone.core.database.MessageDao
import dev.agentone.core.database.MemoryEntryDao
import dev.agentone.core.model.AgentRun
import dev.agentone.core.model.AgentStepLog
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.model.ChatSession
import dev.agentone.core.model.ProviderConfig
import dev.agentone.core.prompt.PromptBuilder
import dev.agentone.core.providers.ChatCompletionRequest
import dev.agentone.core.providers.MessageRole
import dev.agentone.core.providers.ProviderRegistry
import dev.agentone.core.providers.ToolCall
import dev.agentone.core.providers.ToolResult
import dev.agentone.core.providers.UnifiedMessage
import dev.agentone.core.tools.RiskLevel
import dev.agentone.core.tools.ToolAppContext
import dev.agentone.core.tools.ToolExecutionRequest
import dev.agentone.core.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class AgentRuntime(
    private val providerRegistry: ProviderRegistry,
    private val toolRegistry: ToolRegistry,
    private val promptBuilder: PromptBuilder,
    private val messageDao: MessageDao,
    private val agentRunDao: AgentRunDao,
    private val agentStepLogDao: AgentStepLogDao,
    private val memoryEntryDao: MemoryEntryDao,
    private val appContext: ToolAppContext,
    private val scope: CoroutineScope,
    private val isAutoApproveLowRisk: () -> Boolean
) {
    private val _events = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 256)
    val events = _events.asSharedFlow()

    private var currentRunId: String? = null
    private var currentJob: Job? = null
    private var approvalDeferred: CompletableDeferred<Set<String>>? = null

    companion object {
        const val MAX_STEPS = 8
    }

    fun runAgent(
        session: ChatSession,
        providerConfig: ProviderConfig,
        userMessage: String
    ) {
        val runId = UUID.randomUUID().toString()
        currentRunId = runId
        val run = AgentRun(id = runId, sessionId = session.id, state = "running")

        currentJob = scope.launch {
            agentRunDao.upsert(run)
            _events.emit(AgentEvent.RunStarted(runId))
            logStep(runId, 0, "thinking", "Starting agent run with ${session.providerId}/${session.modelId}")

            try {
                val history = messageDao.getBySession(session.id)
                val messages = mutableListOf<UnifiedMessage>()

                val memories = memoryEntryDao.observeAll().first()

                val systemPrompt = promptBuilder.buildSystemPrompt(
                    memories = memories.take(5).map { "[${it.title}]: ${it.content.take(200)}" }
                )
                messages.add(UnifiedMessage(role = MessageRole.SYSTEM, content = systemPrompt))

                history.forEach { msg ->
                    messages.add(
                        UnifiedMessage(
                            role = when (msg.role) {
                                "user" -> MessageRole.USER
                                "assistant" -> MessageRole.ASSISTANT
                                "system" -> MessageRole.SYSTEM
                                "tool_call" -> MessageRole.ASSISTANT
                                "tool_result" -> MessageRole.TOOL
                                else -> MessageRole.USER
                            },
                            content = msg.content,
                            toolCalls = if (msg.role == "tool_call" && msg.toolCallId != null) {
                                listOf(ToolCall(id = msg.toolCallId, name = msg.toolName ?: "", argumentsJson = msg.content))
                            } else null,
                            toolCallId = if (msg.role == "tool_result") msg.toolCallId else null,
                            name = msg.toolName
                        )
                    )
                }

                messages.add(UnifiedMessage(role = MessageRole.USER, content = userMessage))
                messageDao.upsert(
                    ChatMessage(id = UUID.randomUUID().toString(), sessionId = session.id, role = "user", content = userMessage)
                )

                val tools = toolRegistry.listByRisk(RiskLevel.MEDIUM).map { it.definition }
                var step = 0
                var isComplete = false

                while (step < MAX_STEPS && isActive && !isComplete) {
                    step++
                    logStep(runId, step, "thinking", "Step $step: calling model")

                    val provider = providerRegistry.getByTypeString(providerConfig.type)
                    val request = ChatCompletionRequest(
                        providerType = providerConfig.type,
                        model = session.modelId,
                        messages = messages.toList(),
                        tools = tools.ifEmpty { null },
                        temperature = 0.7f,
                        maxTokens = 4096,
                        stream = false
                    )

                    val result = provider.complete(request, providerConfig)
                    val toolCalls = result.requestedToolCalls

                    if (toolCalls.isEmpty()) {
                        val content = result.assistantMessage.content ?: ""
                        messages.add(UnifiedMessage(role = MessageRole.ASSISTANT, content = content))
                        messageDao.upsert(
                            ChatMessage(id = UUID.randomUUID().toString(), sessionId = session.id, role = "assistant", content = content)
                        )
                        _events.emit(AgentEvent.AssistantTextDelta(content))
                        logStep(runId, step, "response", "Assistant responded (${content.length} chars)")
                        isComplete = true
                        break
                    }

                    _events.emit(AgentEvent.ToolCallRequested(toolCalls))
                    logStep(runId, step, "tool_call", "Requested ${toolCalls.size} tool(s): ${toolCalls.joinToString { it.name }}")

                    val (autoCalls, approvalCalls) = toolCalls.partition { tc ->
                        val risk = toolRegistry.get(tc.name)?.definition?.riskLevel
                            ?.let { try { RiskLevel.valueOf(it.uppercase()) } catch (_: Exception) { RiskLevel.LOW } }
                            ?: RiskLevel.LOW
                        (risk == RiskLevel.LOW || risk == RiskLevel.MEDIUM) && isAutoApproveLowRisk()
                    }

                    val toExecute = if (approvalCalls.isNotEmpty()) {
                        _events.emit(AgentEvent.ToolApprovalRequired(approvalCalls))
                        agentRunDao.upsert(run.copy(state = "waiting_approval", stepCount = step))

                        val deferred = CompletableDeferred<Set<String>>()
                        approvalDeferred = deferred
                        val approvedIds = deferred.await()
                        approvalDeferred = null

                        val rejected = approvalCalls.filter { it.id !in approvedIds }
                        rejected.forEach { tc ->
                            _events.emit(AgentEvent.ToolRejected(tc.id))
                            val rejectionMsg = ToolResult(
                                toolCallId = tc.id, name = tc.name, success = false,
                                errorCode = "USER_REJECTED", outputText = "User rejected tool execution"
                            )
                            _events.emit(AgentEvent.ToolExecutionCompleted(rejectionMsg))
                        }

                        val approved = approvalCalls.filter { it.id in approvedIds }
                        agentRunDao.upsert(run.copy(state = "running", stepCount = step))
                        autoCalls + approved
                    } else {
                        toolCalls
                    }

                    if (toExecute.isEmpty()) {
                        val msg = "All tool calls were rejected by user."
                        messages.add(UnifiedMessage(role = MessageRole.ASSISTANT, content = msg))
                        messageDao.upsert(
                            ChatMessage(id = UUID.randomUUID().toString(), sessionId = session.id, role = "assistant", content = msg)
                        )
                        _events.emit(AgentEvent.AssistantTextDelta(msg))
                        isComplete = true
                        break
                    }

                    // Save tool_call assistant message with the tool calls
                    toExecute.forEach { tc ->
                        messageDao.upsert(
                            ChatMessage(
                                id = UUID.randomUUID().toString(), sessionId = session.id, role = "tool_call",
                                content = tc.argumentsJson, toolName = tc.name, toolCallId = tc.id,
                                status = "requested"
                            )
                        )
                        messages.add(
                            UnifiedMessage(
                                role = MessageRole.ASSISTANT,
                                content = null,
                                toolCalls = listOf(tc)
                            )
                        )
                    }

                    executeToolCalls(toExecute, runId, session.id, messages)
                }

                if (step >= MAX_STEPS) {
                    val summary = "Reached maximum steps ($MAX_STEPS). Task may be incomplete."
                    messageDao.upsert(
                        ChatMessage(id = UUID.randomUUID().toString(), sessionId = session.id, role = "system", content = summary)
                    )
                }

                agentRunDao.upsert(run.copy(state = "completed", stepCount = step, finishedAt = System.currentTimeMillis()))
                _events.emit(AgentEvent.RunCompleted(runId, step))
            } catch (e: CancellationException) {
                agentRunDao.upsert(run.copy(state = "cancelled", lastError = "Cancelled", finishedAt = System.currentTimeMillis()))
                _events.emit(AgentEvent.RunCancelled(runId))
            } catch (e: Exception) {
                if (isActive) {
                    agentRunDao.upsert(run.copy(state = "failed", lastError = e.message, finishedAt = System.currentTimeMillis()))
                    _events.emit(AgentEvent.RunFailed(runId, e.message ?: "Unknown error"))
                }
            } finally {
                approvalDeferred = null
            }
        }
    }

    fun continueAfterApproval(runId: String, approvedCallIds: Set<String>) {
        approvalDeferred?.complete(approvedCallIds)
    }

    fun cancelRun() {
        currentJob?.cancel()
        currentJob = null
    }

    private suspend fun executeToolCalls(
        calls: List<ToolCall>,
        runId: String,
        sessionId: String,
        messages: MutableList<UnifiedMessage>
    ) {
        for (call in calls) {
            _events.emit(AgentEvent.ToolExecutionStarted(call.id, call.name))
            logStep(runId, 0, "tool_exec", "Executing ${call.name}")

            val tool = toolRegistry.get(call.name)
            val result = if (tool != null) {
                tool.execute(
                    ToolExecutionRequest(
                        toolCallId = call.id, toolName = call.name, argumentsJson = call.argumentsJson,
                        sessionId = sessionId, runId = runId, appContext = appContext
                    )
                )
            } else {
                ToolResult(toolCallId = call.id, name = call.name, success = false, errorCode = "TOOL_NOT_FOUND", outputText = "Tool not found: ${call.name}")
            }

            _events.emit(AgentEvent.ToolExecutionCompleted(result))
            logStep(runId, 0, "tool_result", "${call.name}: ${if (result.success) "success" else "failed: ${result.errorCode}"}")

            messageDao.upsert(
                ChatMessage(
                    id = UUID.randomUUID().toString(), sessionId = sessionId, role = "tool_result",
                    content = result.outputText, toolName = call.name, toolCallId = call.id,
                    status = if (result.success) "executed" else "failed"
                )
            )

            messages.add(
                UnifiedMessage(
                    role = MessageRole.TOOL,
                    content = if (result.success) result.outputText else "Error (${result.errorCode}): ${result.outputText}",
                    toolCallId = result.toolCallId, name = result.name
                )
            )
        }
    }

    private suspend fun logStep(runId: String, index: Int, type: String, summary: String) {
        agentStepLogDao.upsert(AgentStepLog(id = UUID.randomUUID().toString(), runId = runId, index = index, type = type, summary = summary))
        _events.emit(AgentEvent.StepLogged(index, summary))
    }
}
