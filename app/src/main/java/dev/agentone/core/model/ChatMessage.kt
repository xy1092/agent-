package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // user, assistant, system, tool_call, tool_result
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val status: String? = null, // pending, approved, rejected, executed, failed
    val createdAt: Long = System.currentTimeMillis()
)
