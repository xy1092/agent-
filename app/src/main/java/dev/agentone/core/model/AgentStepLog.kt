package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_step_logs",
    foreignKeys = [ForeignKey(
        entity = AgentRun::class,
        parentColumns = ["id"],
        childColumns = ["runId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("runId")]
)
data class AgentStepLog(
    @PrimaryKey val id: String,
    val runId: String,
    val index: Int,
    val type: String, // thinking, tool_call, tool_result, response
    val summary: String,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
