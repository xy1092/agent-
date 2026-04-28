package dev.agentone.core.providers

import dev.agentone.core.model.ProviderConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class FakeProvider : Provider {
    override val type = ProviderType.FAKE

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): ChatCompletionResult {
        delay(100)
        val lastUserMsg = request.messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val response = generateFakeResponse(lastUserMsg, request.tools)
        return ChatCompletionResult(
            assistantMessage = response.first,
            requestedToolCalls = response.second,
            finishReason = "stop"
        )
    }

    override fun streamComplete(
        request: ChatCompletionRequest,
        config: ProviderConfig
    ): Flow<ChatCompletionChunk> = flow {
        val result = complete(request, config)
        val text = result.assistantMessage.content ?: ""
        val words = text.split(" ")
        for (word in words) {
            delay(30)
            emit(ChatCompletionChunk(deltaText = "$word "))
        }
        if (result.requestedToolCalls.isNotEmpty()) {
            delay(100)
            emit(
                ChatCompletionChunk(
                    toolCallsDelta = result.requestedToolCalls,
                    finishReason = "tool_calls"
                )
            )
        } else {
            emit(ChatCompletionChunk(finishReason = "stop"))
        }
    }

    override fun defaultModels() = listOf("fake-v1")

    private fun generateFakeResponse(
        userMessage: String,
        tools: List<ToolDefinition>?
    ): Pair<UnifiedMessage, List<ToolCall>> {
        val msg = userMessage.lowercase()

        if (tools != null && tools.any { it.name == "read_file" } && msg.contains("read") && (msg.contains("file") || msg.contains("txt"))) {
            val fileName = Regex("""['"]?([\w./-]+\.\w+)['"]?""").find(userMessage)?.groupValues?.get(1) ?: "unknown.txt"
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "read_file",
                argumentsJson = """{"path":"$fileName"}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "Let me read that file for you.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "write_file" } && (msg.contains("write") || msg.contains("save") || msg.contains("create"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "write_file",
                argumentsJson = """{"path":"note.txt","content":"This is a test note created by AgentOne."}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "I'll create that file for you.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "list_files" } && msg.contains("list") && (msg.contains("file") || msg.contains("director"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "list_files",
                argumentsJson = """{"path":"."}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "Let me list the files for you.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "create_reminder" } && (msg.contains("remind") || msg.contains("reminder"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "create_reminder",
                argumentsJson = """{"title":"Reminder from AgentOne","note":"$userMessage","dueInMinutes":60}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "I'll create a reminder for you.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "save_memory" } && (msg.contains("remember") || msg.contains("memory") || msg.contains("save this"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "save_memory",
                argumentsJson = """{"title":"Memory from conversation","content":"$userMessage","tags":"agentone"}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "I'll save this to memory.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "open_url" } && (msg.contains("open") || msg.contains("browse") || msg.contains("http"))) {
            val url = Regex("""(https?://\S+)""").find(userMessage)?.groupValues?.get(1) ?: "https://example.com"
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "open_url",
                argumentsJson = """{"url":"$url"}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "Let me open that page for you.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        if (tools != null && tools.any { it.name == "list_calendar_events" } && msg.contains("calendar") && (msg.contains("list") || msg.contains("show") || msg.contains("what"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "list_calendar_events",
                argumentsJson = """{"daysAhead":7}"""
            )
            return UnifiedMessage(
                role = MessageRole.ASSISTANT,
                content = "Let me check your calendar.",
                toolCalls = listOf(toolCall)
            ) to listOf(toolCall)
        }

        return UnifiedMessage(
            role = MessageRole.ASSISTANT,
            content = "I'm the FakeProvider. You said: \"$userMessage\". This is a simulated response for development and testing. In production, connect a real API key to get actual AI responses."
        ) to emptyList()
    }
}
