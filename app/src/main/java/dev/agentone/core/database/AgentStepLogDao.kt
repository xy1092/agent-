package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.AgentStepLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentStepLogDao {
    @Query("SELECT * FROM agent_step_logs WHERE runId = :runId ORDER BY createdAt ASC")
    fun observeByRun(runId: String): Flow<List<AgentStepLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: AgentStepLog)

    @Query("DELETE FROM agent_step_logs WHERE runId = :runId")
    suspend fun deleteByRun(runId: String)
}
