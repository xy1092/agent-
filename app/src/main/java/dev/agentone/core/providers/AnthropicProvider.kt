package dev.agentone.core.providers

import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicProvider : Provider {
    override val type = ProviderType.ANTHROPIC

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val apiKey = config.encryptedApiKeyRef ?: throw IllegalStateException("API key not configured")
        val model = request.model.ifEmpty { "claude-sonnet-4-6" }

        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.toDouble())
            putJsonArray("messages") {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.TOOL -> "user"
                            MessageRole.SYSTEM -> "user"
                        })
                        when {
                            msg.role == MessageRole.TOOL && msg.toolCallId != null -> {
                                putJsonArray("content") {
                                    add(buildJsonObject {
                                        put("type", "tool_result")
                                        put("tool_use_id", msg.toolCallId)
                                        put("content", msg.content ?: "")
                                    })
                                }
                            }
                            msg.role == MessageRole.ASSISTANT && msg.toolCalls != null -> {
                                putJsonArray("content") {
                                    msg.toolCalls.forEach { tc ->
                                        add(buildJsonObject {
                                            put("type", "tool_use")
                                            put("id", tc.id)
                                            put("name", tc.name)
                                            put("input", json.parseToJsonElement(tc.argumentsJson))
                                        })
                                    }
                                }
                            }
                            else -> {
                                put("content", msg.content ?: "")
                            }
                        }
                    })
                }
            }
            val systemMsg = request.messages.find { it.role == MessageRole.SYSTEM }?.content
            if (systemMsg != null) {
                put("system", systemMsg)
            }
            request.tools?.let { tools ->
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", json.parseToJsonElement(tool.inputSchemaJson))
                        })
                    }
                }
            }
        }

        val httpRequest = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        if (!response.isSuccessful) throw IllegalStateException("Anthropic API error ${response.code}: $body")

        parseAnthropicResponse(body)
    }

    override fun streamComplete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): Flow<ChatCompletionChunk> = flow {
        val result = complete(request.copy(stream = false), config)
        val words = (result.assistantMessage.content ?: "").split(" ")
        for (word in words) {
            emit(ChatCompletionChunk(deltaText = "$word "))
        }
        if (result.requestedToolCalls.isNotEmpty()) {
            emit(ChatCompletionChunk(toolCallsDelta = result.requestedToolCalls, finishReason = "tool_calls"))
        } else {
            emit(ChatCompletionChunk(finishReason = "stop"))
        }
    }.flowOn(Dispatchers.IO)

    override fun supportsStreaming(model: String): Boolean = true

    override fun defaultModels() = listOf(
        "claude-sonnet-4-6",
        "claude-opus-4-7",
        "claude-haiku-4-5"
    )

    private fun parseAnthropicResponse(body: String): ChatCompletionResult {
        val obj = json.parseToJsonElement(body).jsonObject
        val content = obj["content"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        for (block in content) {
            val b = block.jsonObject
            when (b["type"]?.jsonPrimitive?.content) {
                "text" -> b["text"]?.jsonPrimitive?.content?.let { textParts.add(it) }
                "tool_use" -> {
                    toolCalls.add(
                        ToolCall(
                            id = b["id"]?.jsonPrimitive?.content ?: "",
                            name = b["name"]?.jsonPrimitive?.content ?: "",
                            argumentsJson = b["input"]?.toString() ?: "{}"
                        )
                    )
                }
            }
        }

        return ChatCompletionResult(
            assistantMessage = UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = textParts.joinToString("\n").ifEmpty { null },
                toolCalls = toolCalls.ifEmpty { null }
            ),
            requestedToolCalls = toolCalls,
            finishReason = obj["stop_reason"]?.jsonPrimitive?.content ?: "stop",
            rawResponse = body
        )
    }
}
