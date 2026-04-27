package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.MountedWorkspace
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM mounted_workspaces ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<MountedWorkspace>>

    @Query("SELECT * FROM mounted_workspaces WHERE id = :id")
    suspend fun getById(id: String): MountedWorkspace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: MountedWorkspace)

    @Query("DELETE FROM mounted_workspaces WHERE id = :id")
    suspend fun deleteById(id: String)
}
