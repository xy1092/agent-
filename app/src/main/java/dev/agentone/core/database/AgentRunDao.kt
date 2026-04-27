package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.AgentRun

@Dao
interface AgentRunDao {
    @Query("SELECT * FROM agent_runs WHERE sessionId = :sessionId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatest(sessionId: String): AgentRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: AgentRun)

    @Query("DELETE FROM agent_runs WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
