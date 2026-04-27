package dev.agentone.core.providers

import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

interface Provider {
    val type: ProviderType

    suspend fun complete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): ChatCompletionResult

    fun streamComplete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): Flow<ChatCompletionChunk>

    fun supportsToolCalling(model: String): Boolean = true

    fun supportsStreaming(model: String): Boolean = true

    fun defaultModels(): List<String>
}
