package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mounted_workspaces")
data class MountedWorkspace(
    @PrimaryKey val id: String,
    val displayName: String,
    val treeUri: String,
    val createdAt: Long = System.currentTimeMillis()
)
