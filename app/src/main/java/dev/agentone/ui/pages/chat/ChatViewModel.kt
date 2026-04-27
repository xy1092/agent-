package dev.agentone.ui.pages.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agentone.AgentOneApp
import dev.agentone.core.agent.AgentEvent
import dev.agentone.core.agent.AgentRuntime
import dev.agentone.core.model.AgentStepLog
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.model.ChatSession
import dev.agentone.core.model.ProviderConfig
import dev.agentone.core.providers.ToolCall
import dev.agentone.core.tools.ToolAppContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    var agentRuntime: AgentRuntime? = null

    init {
        viewModelScope.launch {
            _session.value = db.sessionDao().getById(sessionId)
            db.messageDao().observeBySession(sessionId).collect { _messages.value = it }
        }
    }

    fun sendMessage(text: String) {
        val session = _session.value ?: return
        val providerConfig = db.providerConfigDao().getById(session.providerId) ?: return

        // Resolve API key from secure storage
        val apiKey = security.getApiKey(session.providerId) ?: ""
        val resolvedConfig = providerConfig.copy(encryptedApiKeyRef = apiKey)

        _isRunning.value = true
        _replyText.value = ""

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
            scope = viewModelScope,
            isAutoApproveLowRisk = { security.isAutoApproveLowRisk() }
        )

        agentRuntime = runtime

        viewModelScope.launch {
            runtime.events.collect { event ->
                handleEvent(event)
            }
        }

        runtime.runAgent(session, resolvedConfig, text)

        // Auto-generate title if the session is new
        if (session.title == "New Session" && _messages.value.size <= 1) {
            val title = if (text.length > 50) text.take(50) + "..." else text
            viewModelScope.launch {
                db.sessionDao().upsert(session.copy(title = title, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun stopGeneration() {
        agentRuntime?.cancelRun()
        _isRunning.value = false
    }

    fun regenerateLast() {
        // Remove last assistant message and resend
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
        agentRuntime?.continueAfterApproval(sessionId, approvedIds)
    }

    fun rejectAllTools() {
        _pendingApprovals.value = emptyList()
        agentRuntime?.continueAfterApproval(sessionId, emptySet())
    }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.RunStarted -> {
                _isRunning.value = true
                viewModelScope.launch {
                    db.agentStepLogDao().observeByRun(event.runId).collect { _agentLogs.value = it }
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
            }
            is AgentEvent.RunFailed -> {
                _isRunning.value = false
                _replyText.value = "[Error: ${event.error}]"
            }
            is AgentEvent.RunCancelled -> {
                _isRunning.value = false
                _replyText.value = ""
            }
        }
    }

    class Factory(private val sessionId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(sessionId) as T
    }
}
