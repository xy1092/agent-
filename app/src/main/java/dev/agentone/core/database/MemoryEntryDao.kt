package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<MemoryEntry>>

    @Query("SELECT * FROM memory_entries WHERE id = :id")
    suspend fun getById(id: String): MemoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MemoryEntry)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}
