package dev.agentone.core.providers

import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader

open class OpenAICompatibleProvider(
    override val type: ProviderType,
    private val defaultEndpoint: String
) : Provider {

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val endpoint = config.endpoint ?: defaultEndpoint
        val apiKey = config.encryptedApiKeyRef ?: throw IllegalStateException("API key not configured")

        val requestBody = buildOpenAIRequestBody(request)
        val httpRequest = Request.Builder()
            .url("$endpoint/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Empty response")
        if (!response.isSuccessful) throw IllegalStateException("API error ${response.code}: $body")

        parseOpenAIResponse(body, request)
    }

    override fun streamComplete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): Flow<ChatCompletionChunk> = flow {
        val endpoint = config.endpoint ?: defaultEndpoint
        val apiKey = config.encryptedApiKeyRef ?: throw IllegalStateException("API key not configured")

        val streamRequest = buildOpenAIRequestBody(request).let { body ->
            buildJsonObject {
                body.forEach { (k, v) -> put(k, v) }
                put("stream", true)
            }
        }

        val httpRequest = Request.Builder()
            .url("$endpoint/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(streamRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("API error ${response.code}: ${response.body?.string()}")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
        reader?.use { br ->
            var accumulatedToolCalls = mutableMapOf<Int, ToolCallBuilder>()
            br.lineSequence().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        emit(ChatCompletionChunk(finishReason = "stop"))
                        return@forEach
                    }
                    try {
                        val chunk = json.parseToJsonElement(data).jsonObject
                        val choices = chunk["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                        val delta = choices?.get("delta")?.jsonObject
                        val finishReason = choices?.get("finish_reason")?.jsonPrimitive?.content

                        if (delta != null) {
                            val text = delta["content"]?.jsonPrimitive?.content
                            if (!text.isNullOrEmpty()) {
                                emit(ChatCompletionChunk(deltaText = text))
                            }

                            val toolCallsDelta = delta["tool_calls"]?.jsonArray
                            if (toolCallsDelta != null) {
                                for (tc in toolCallsDelta) {
                                    val tcObj = tc.jsonObject
                                    val idx = tcObj["index"]?.jsonPrimitive?.int ?: 0
                                    val builder = accumulatedToolCalls.getOrPut(idx) { ToolCallBuilder() }
                                    tcObj["id"]?.jsonPrimitive?.content?.let { builder.id = it }
                                    tcObj["function"]?.jsonObject?.let { fn ->
                                        fn["name"]?.jsonPrimitive?.content?.let { builder.name = it }
                                        fn["arguments"]?.jsonPrimitive?.content?.let { builder.arguments.append(it) }
                                    }
                                }
                            }
                        }

                        if (finishReason == "tool_calls") {
                            val toolCalls = accumulatedToolCalls.values.map { it.build() }
                            emit(ChatCompletionChunk(toolCallsDelta = toolCalls, finishReason = "tool_calls"))
                        } else if (finishReason != null && finishReason != "null") {
                            emit(ChatCompletionChunk(finishReason = finishReason))
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun defaultModels(): List<String> = when (type) {
        ProviderType.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo")
        ProviderType.OPENROUTER -> listOf("openai/gpt-4o", "anthropic/claude-sonnet-4-6")
        ProviderType.OPENAI_COMPATIBLE -> listOf("default")
        else -> listOf("default")
    }

    private fun buildOpenAIRequestBody(request: ChatCompletionRequest): JsonObject {
        return buildJsonObject {
            put("model", request.model)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            putJsonArray("messages") {
                request.messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role.name.lowercase())
                        msg.content?.let { put("content", it) }
                        msg.toolCalls?.let { calls ->
                            putJsonArray("tool_calls") {
                                calls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", tc.name)
                                            put("arguments", tc.argumentsJson)
                                        }
                                    })
                                }
                            }
                        }
                        msg.toolCallId?.let { put("tool_call_id", it) }
                    })
                }
            }
            request.tools?.let { tools ->
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", json.parseToJsonElement(tool.inputSchemaJson))
                            }
                        })
                    }
                }
            }
        }
    }

    private fun parseOpenAIResponse(
        body: String,
        request: ChatCompletionRequest
    ): ChatCompletionResult {
        val obj = json.parseToJsonElement(body).jsonObject
        val choice = obj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
        val message = choice?.get("message")?.jsonObject

        val content = message?.get("content")?.jsonPrimitive?.content
        val toolCalls = message?.get("tool_calls")?.jsonArray?.map { tc ->
            val tcObj = tc.jsonObject
            val fn = tcObj["function"]?.jsonObject
            ToolCall(
                id = tcObj["id"]?.jsonPrimitive?.content ?: "",
                name = fn?.get("name")?.jsonPrimitive?.content ?: "",
                argumentsJson = fn?.get("arguments")?.jsonPrimitive?.content ?: "{}"
            )
        } ?: emptyList()

        return ChatCompletionResult(
            assistantMessage = UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = content,
                toolCalls = toolCalls.ifEmpty { null }
            ),
            requestedToolCalls = toolCalls,
            finishReason = choice?.get("finish_reason")?.jsonPrimitive?.content ?: "stop",
            rawResponse = body
        )
    }

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build() = ToolCall(
            id = id,
            name = name,
            argumentsJson = arguments.toString()
        )
    }
}
