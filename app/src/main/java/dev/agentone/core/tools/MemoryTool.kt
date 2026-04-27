package dev.agentone.core.tools

import dev.agentone.core.database.MemoryEntryDao
import dev.agentone.core.model.MemoryEntry
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class MemoryTool(
    private val memoryEntryDao: MemoryEntryDao,
    private val isMemoryEnabled: () -> Boolean
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val saveMemoryTool = object : Tool {
        override val definition = ToolDefinition(
            name = "save_memory",
            description = "Save information to long-term memory for future reference",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Memory title"},"content":{"type":"string","description":"What to remember"},"tags":{"type":"string","description":"Comma-separated tags"}},"required":["title","content"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            if (!isMemoryEnabled()) return error(request, "MEMORY_DISABLED", "Memory feature is disabled")
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val content = args["content"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing content")
                val tags = args["tags"]?.jsonPrimitive?.content ?: ""

                val entry = MemoryEntry(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    tags = tags,
                    sourceSessionId = request.sessionId
                )
                memoryEntryDao.upsert(entry)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "save_memory",
                    success = true,
                    outputText = "Memory saved: $title"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val searchMemoryTool = object : Tool {
        override val definition = ToolDefinition(
            name = "search_memory",
            description = "Search through saved memories by keyword",
            inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            if (!isMemoryEnabled()) return error(request, "MEMORY_DISABLED", "Memory feature is disabled")
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val query = args["query"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing query")
                val results = memoryEntryDao.search(query).first()
                val output = if (results.isEmpty()) "No memories found for '$query'" else {
                    results.joinToString("\n---\n") { m ->
                        "Title: ${m.title}\nTags: ${m.tags.ifEmpty { "none" }}\n${m.content}"
                    }
                }
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "search_memory",
                    success = true,
                    outputText = output
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val deleteMemoryTool = object : Tool {
        override val definition = ToolDefinition(
            name = "delete_memory",
            description = "Delete a memory entry by title",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Title of memory to delete"}},"required":["title"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            if (!isMemoryEnabled()) return error(request, "MEMORY_DISABLED", "Memory feature is disabled")
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val all = memoryEntryDao.observeAll().first()
                val match = all.find { it.title.contains(title, ignoreCase = true) }
                if (match == null) {
                    return ToolResult(
                        toolCallId = request.toolCallId,
                        name = "delete_memory",
                        success = false,
                        outputText = "No memory found matching '$title'"
                    )
                }
                memoryEntryDao.deleteById(match.id)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "delete_memory",
                    success = true,
                    outputText = "Memory deleted: ${match.title}"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    fun getAll(): List<Tool> = listOf(saveMemoryTool, searchMemoryTool, deleteMemoryTool)

    private fun error(request: ToolExecutionRequest, code: String, message: String) = ToolResult(
        toolCallId = request.toolCallId,
        name = request.toolName,
        success = false,
        errorCode = code,
        outputText = message
    )
}
