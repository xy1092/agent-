package dev.agentone.ui.pages.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.agentone.AgentOneApp
import dev.agentone.core.model.ChatSession
import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SessionsViewModel : ViewModel() {
    private val db = AgentOneApp.instance.database

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    init {
        viewModelScope.launch {
            db.sessionDao().observeAll().collect { _sessions.value = it }
        }
    }

    fun createSession(providerId: String, modelId: String, onCreated: (String) -> Unit) {
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            val session = ChatSession(
                id = sessionId,
                title = "New Session",
                providerId = providerId,
                modelId = modelId
            )
            db.sessionDao().upsert(session)
            onCreated(sessionId)
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { db.sessionDao().deleteById(id) }
    }

    fun togglePin(id: String, pinned: Boolean) {
        viewModelScope.launch { db.sessionDao().setPinned(id, pinned) }
    }

    fun updateTitle(id: String, title: String) {
        viewModelScope.launch {
            db.sessionDao().getById(id)?.let { session ->
                db.sessionDao().upsert(session.copy(title = title, updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
