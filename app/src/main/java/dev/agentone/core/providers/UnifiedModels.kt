package dev.agentone.core.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    @SerialName("arguments_json")
    val argumentsJson: String
)

@Serializable
data class ToolResult(
    @SerialName("tool_call_id")
    val toolCallId: String,
    val name: String,
    val success: Boolean = true,
    @SerialName("output_text")
    val outputText: String = "",
    @SerialName("output_json")
    val outputJson: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null
)

@Serializable
data class UnifiedMessage(
    val role: MessageRole,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema_json")
    val inputSchemaJson: String,
    @SerialName("risk_level")
    val riskLevel: String = "LOW" // LOW, MEDIUM, HIGH
)

@Serializable
data class ChatCompletionRequest(
    @SerialName("provider_type")
    val providerType: String,
    val model: String,
    val messages: List<UnifiedMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    val temperature: Float = 0.7f,
    @SerialName("max_tokens")
    val maxTokens: Int = 4096,
    val stream: Boolean = true
)

data class ChatCompletionChunk(
    val deltaText: String? = null,
    val toolCallsDelta: List<ToolCall>? = null,
    val finishReason: String? = null
)

data class ChatCompletionResult(
    val assistantMessage: UnifiedMessage,
    val requestedToolCalls: List<ToolCall> = emptyList(),
    val finishReason: String = "stop",
    val rawResponse: String? = null
)
