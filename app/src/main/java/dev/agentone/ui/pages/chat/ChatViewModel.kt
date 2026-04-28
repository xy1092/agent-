package dev.agentone.ui.pages.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agentone.AgentOneApp
import dev.agentone.core.agent.AgentEvent
import dev.agentone.core.agent.AgentRuntime
import dev.agentone.core.model.AgentStepLog
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.model.ChatSession
import dev.agentone.core.providers.ToolCall
import dev.agentone.core.tools.ToolAppContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChatViewModel(private val sessionId: String) : ViewModel() {
    private val app = AgentOneApp.instance
    private val db = app.database
    private val security = app.securityManager

    private val _session = MutableStateFlow<ChatSession?>(null)
    val session: StateFlow<ChatSession?> = _session.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ToolCall>>(emptyList())
    val pendingApprovals: StateFlow<List<ToolCall>> = _pendingApprovals.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<AgentStepLog>>(emptyList())
    val agentLogs: StateFlow<List<AgentStepLog>> = _agentLogs.asStateFlow()

    private val _replyText = MutableStateFlow("")
    val replyText: StateFlow<String> = _replyText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var agentRuntime: AgentRuntime? = null
        private set

    private var currentRunId: String? = null
    private var eventCollectJob: Job? = null
    private var agentLogCollectJob: Job? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("ChatViewModel", "Unhandled coroutine exception", e)
        _isRunning.value = false
        _errorMessage.value = "发生错误: ${e.message}"
    }

    init {
        viewModelScope.launch {
            try {
                _session.value = db.sessionDao().getById(sessionId)
                db.messageDao().observeBySession(sessionId).collect { _messages.value = it }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to load session", e)
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch(exceptionHandler) {
            // Wait for session to load
            var s = _session.value
            if (s == null) {
                var retries = 0
                while (s == null && retries < 30) {
                    delay(100)
                    s = _session.value
                    retries++
                }
            }
            val session = s
            if (session == null) {
                _errorMessage.value = "会话加载失败，请返回重试"
                return@launch
            }

            val providerConfig = db.providerConfigDao().getById(session.providerId)
            if (providerConfig == null) {
                _errorMessage.value = "提供商配置未找到: ${session.providerId}"
                return@launch
            }

            val apiKey = security.getApiKey(session.providerId) ?: ""
            val resolvedConfig = providerConfig.copy(encryptedApiKeyRef = apiKey)

            _isRunning.value = true
            _replyText.value = ""
            _errorMessage.value = null

            val filesDir = File(app.filesDir, "workspace")
            filesDir.mkdirs()

            val appContext = ToolAppContext(
                filesDir = filesDir,
                contentResolver = app.contentResolver
            )

            val runtime = AgentRuntime(
                providerRegistry = app.providerRegistry,
                toolRegistry = app.toolRegistry,
                promptBuilder = app.promptBuilder,
                messageDao = db.messageDao(),
                agentRunDao = db.agentRunDao(),
                agentStepLogDao = db.agentStepLogDao(),
                memoryEntryDao = db.memoryEntryDao(),
                appContext = appContext,
                isAutoApproveLowRisk = { security.isAutoApproveLowRisk() }
            )

            agentRuntime = runtime

            // Cancel previous event/log collectors before starting new ones
            eventCollectJob?.cancel()
            agentLogCollectJob?.cancel()

            // Launch permanent event collector for this runtime
            eventCollectJob = launch {
                try {
                    runtime.events.collect { event -> handleEvent(event) }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Event collection error", e)
                }
            }

            try {
                // runAgent is now a suspend function - it blocks until complete or cancelled
                runtime.runAgent(session, resolvedConfig, text)

                // Auto-generate title if the session is new
                if (session.title == "New Session") {
                    try {
                        val title = if (text.length > 50) text.take(50) + "..." else text
                        db.sessionDao().upsert(session.copy(title = title, updatedAt = System.currentTimeMillis()))
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to update title", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "runAgent failed", e)
                _errorMessage.value = "运行失败: ${e.message}"
            } finally {
                eventCollectJob?.cancel()
                agentLogCollectJob?.cancel()
                agentRuntime = null
            }
        }
    }

    fun stopGeneration() {
        agentRuntime?.cancelRun()
        _isRunning.value = false
    }

    fun regenerateLast() {
        viewModelScope.launch {
            val msgs = _messages.value.toMutableList()
            msgs.removeLastOrNull()
            val lastUserMsg = msgs.findLast { it.role == "user" }
            if (lastUserMsg != null) {
                sendMessage(lastUserMsg.content)
            }
        }
    }

    fun approveTools(approvedIds: Set<String>) {
        _pendingApprovals.value = emptyList()
        agentRuntime?.continueAfterApproval(currentRunId ?: "", approvedIds)
    }

    fun rejectAllTools() {
        _pendingApprovals.value = emptyList()
        agentRuntime?.continueAfterApproval(currentRunId ?: "", emptySet())
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun handleEvent(event: AgentEvent) {
        try {
            when (event) {
                is AgentEvent.RunStarted -> {
                    currentRunId = event.runId
                    _isRunning.value = true
                    // Cancel previous log collector
                    agentLogCollectJob?.cancel()
                    agentLogCollectJob = viewModelScope.launch {
                        try {
                            db.agentStepLogDao().observeByRun(event.runId).collect { _agentLogs.value = it }
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Agent log collection error", e)
                        }
                    }
                }
                is AgentEvent.AssistantTextDelta -> {
                    _replyText.value = _replyText.value + event.text
                }
                is AgentEvent.ToolCallRequested -> {}
                is AgentEvent.ToolApprovalRequired -> {
                    _pendingApprovals.value = event.toolCalls
                }
                is AgentEvent.ToolApproved -> {}
                is AgentEvent.ToolRejected -> {}
                is AgentEvent.ToolExecutionStarted -> {}
                is AgentEvent.ToolExecutionCompleted -> {}
                is AgentEvent.StepLogged -> {}
                is AgentEvent.RunCompleted -> {
                    _isRunning.value = false
                    _replyText.value = ""
                    currentRunId = null
                }
                is AgentEvent.RunFailed -> {
                    _isRunning.value = false
                    _replyText.value = "[错误: ${event.error}]"
                    currentRunId = null
                }
                is AgentEvent.RunCancelled -> {
                    _isRunning.value = false
                    _replyText.value = ""
                    currentRunId = null
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "handleEvent error", e)
        }
    }

    class Factory(private val sessionId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(sessionId) as T
    }
}
