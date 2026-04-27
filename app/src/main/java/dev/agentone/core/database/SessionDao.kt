package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM chat_sessions WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChatSession)

    @Query("UPDATE chat_sessions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE chat_sessions SET archived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()
}
