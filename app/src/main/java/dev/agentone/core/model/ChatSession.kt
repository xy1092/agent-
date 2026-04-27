package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    val pinned: Boolean = false,
    val providerId: String,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val autoApproveLowRiskTools: Boolean = false,
    val archived: Boolean = false
)
