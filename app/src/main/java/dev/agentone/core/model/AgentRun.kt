package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_runs",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class AgentRun(
    @PrimaryKey val id: String,
    val sessionId: String,
    val state: String = "idle", // idle, running, waiting_approval, completed, failed, cancelled
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val stepCount: Int = 0,
    val lastError: String? = null
)
