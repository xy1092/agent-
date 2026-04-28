package dev.agentone.core.providers

import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

class GeminiProvider : Provider {
    override val type = ProviderType.GEMINI

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
        val model = request.model.ifEmpty { "gemini-2.0-flash" }

        val systemInstructions = request.messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n") { it.content ?: "" }

        val contents = buildJsonObject {
            putJsonArray("contents") {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "model"
                            MessageRole.TOOL -> "user"
                            MessageRole.SYSTEM -> "user"
                        })
                        putJsonArray("parts") {
                            msg.content?.let { content ->
                                add(buildJsonObject { put("text", content) })
                            }
                            msg.toolCalls?.forEach { tc ->
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", tc.name)
                                        put("args", json.parseToJsonElement(tc.argumentsJson))
                                    })
                                })
                            }
                            msg.toolCallId?.let { tcId ->
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", msg.name ?: "")
                                        put("response", buildJsonObject {
                                            put("content", msg.content ?: "")
                                        })
                                    })
                                })
                            }
                        }
                    })
                }
            }
            if (systemInstructions.isNotEmpty()) {
                putJsonArray("system_instruction") {
                    add(buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", systemInstructions) })
                        }
                    })
                }
            }
            request.tools?.let { tools ->
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", json.parseToJsonElement(tool.inputSchemaJson))
                                })
                            }
                        }
                    })
                }
            }
        }

        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(contents.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        if (!response.isSuccessful) throw IllegalStateException("Gemini API error ${response.code}: $body")

        parseGeminiResponse(body)
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
        "gemini-2.0-flash",
        "gemini-2.0-pro",
        "gemini-2.5-pro"
    )

    private fun parseGeminiResponse(body: String): ChatCompletionResult {
        val obj = json.parseToJsonElement(body).jsonObject
        val candidates = obj["candidates"]?.jsonArray.orEmpty()
        val candidate = candidates.firstOrNull()?.jsonObject ?: return ChatCompletionResult(
            assistantMessage = UnifiedMessage(role = MessageRole.ASSISTANT, content = ""),
            finishReason = "error"
        )
        val content = candidate["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray.orEmpty()

        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        for (part in parts) {
            val p = part.jsonObject
            if (p.containsKey("text")) {
                textParts.add(p["text"]!!.jsonPrimitive.content)
            }
            if (p.containsKey("functionCall")) {
                val fc = p["functionCall"]!!.jsonObject
                toolCalls.add(
                    ToolCall(
                        id = "gc-${System.currentTimeMillis()}-${toolCalls.size}",
                        name = fc["name"]?.jsonPrimitive?.content ?: "",
                        argumentsJson = fc["args"]?.toString() ?: "{}"
                    )
                )
            }
        }

        val finishReason = when (candidate["finishReason"]?.jsonPrimitive?.content) {
            "STOP" -> "stop"
            "MAX_TOKENS" -> "length"
            else -> "stop"
        }

        return ChatCompletionResult(
            assistantMessage = UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = textParts.joinToString("").ifEmpty { null },
                toolCalls = toolCalls.ifEmpty { null }
            ),
            requestedToolCalls = toolCalls,
            finishReason = finishReason,
            rawResponse = body
        )
    }
}
