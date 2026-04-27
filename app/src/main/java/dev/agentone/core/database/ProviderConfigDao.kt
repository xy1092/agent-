package dev.agentone.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProviderConfig>>

    @Query("SELECT * FROM provider_configs WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun getEnabled(): List<ProviderConfig>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getById(id: String): ProviderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ProviderConfig)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}
