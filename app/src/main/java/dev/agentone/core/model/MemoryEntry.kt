package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val tags: String = "", // comma-separated
    val sourceSessionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
