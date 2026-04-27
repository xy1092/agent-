package dev.agentone.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfig(
    @PrimaryKey val id: String,
    val type: String, // OPENAI, ANTHROPIC, GEMINI, OPENROUTER, OPENAI_COMPATIBLE, FAKE
    val displayName: String,
    val endpoint: String? = null,
    val encryptedApiKeyRef: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
