package dev.agentone.core.tools

import android.content.Context
import android.provider.DocumentsContract
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class FileTool(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val readFileTool = object : Tool {
        override val definition = ToolDefinition(
            name = "read_file",
            description = "Read the contents of a text file at the given path",
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string","description":"Relative file path to read"}},"required":["path"]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val path = args["path"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing path")
                val file = resolveFile(path, request)
                if (!file.exists()) return error(request, "FILE_NOT_FOUND", "File not found: $path")
                if (!file.canRead()) return error(request, "FILE_ACCESS_DENIED", "Cannot read: $path")
                val content = file.readText().take(50_000)
                val truncated = if (content.length >= 50_000) "\n\n[Content truncated at 50000 chars]" else ""
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "read_file",
                    success = true,
                    outputText = content + truncated
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val writeFileTool = object : Tool {
        override val definition = ToolDefinition(
            name = "write_file",
            description = "Write content to a text file. Creates directories as needed.",
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string","description":"Relative file path"},"content":{"type":"string","description":"Text content to write"}},"required":["path","content"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val path = args["path"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing path")
                val content = args["content"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing content")
                val file = resolveFile(path, request)
                file.parentFile?.mkdirs()
                file.writeText(content)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "write_file",
                    success = true,
                    outputText = "File written: $path (${content.length} chars)"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val listFilesTool = object : Tool {
        override val definition = ToolDefinition(
            name = "list_files",
            description = "List files and directories at the given path",
            inputSchemaJson = """{"type":"object","properties":{"path":{"type":"string","description":"Relative directory path (default: root)"}},"required":[]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val path = args["path"]?.jsonPrimitive?.content ?: "."
                val dir = resolveFile(path, request)
                if (!dir.exists() || !dir.isDirectory) return error(request, "FILE_NOT_FOUND", "Not a directory: $path")
                val listing = dir.listFiles()?.map { f ->
                    val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                    val size = if (f.isFile) " (${f.length()} bytes)" else ""
                    "$type ${f.name}$size"
                }?.joinToString("\n") ?: "Empty directory"
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "list_files",
                    success = true,
                    outputText = listing
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val searchFilesTool = object : Tool {
        override val definition = ToolDefinition(
            name = "search_files",
            description = "Search for files by name pattern in the workspace",
            inputSchemaJson = """{"type":"object","properties":{"pattern":{"type":"string","description":"Search pattern (substring match)"}},"required":["pattern"]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val pattern = args["pattern"]?.jsonPrimitive?.content?.lowercase() ?: return error(request, "INVALID_ARGUMENTS", "Missing pattern")
                val root = resolveFile(".", request)
                val results = mutableListOf<String>()
                searchRecursive(root, pattern, results, 100)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "search_files",
                    success = true,
                    outputText = if (results.isEmpty()) "No files found matching '$pattern'" else results.joinToString("\n")
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }

        private fun searchRecursive(dir: File, pattern: String, results: MutableList<String>, maxResults: Int) {
            if (results.size >= maxResults) return
            dir.listFiles()?.forEach { f ->
                if (f.name.lowercase().contains(pattern)) {
                    results.add("${if (f.isDirectory) "[DIR]" else "[FILE]"} ${f.absolutePath}")
                }
                if (f.isDirectory && results.size < maxResults) {
                    searchRecursive(f, pattern, results, maxResults)
                }
            }
        }
    }

    val createNoteFileTool = object : Tool {
        override val definition = ToolDefinition(
            name = "create_note_file",
            description = "Create a new note file with a title and content",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Note title (used as filename)"},"content":{"type":"string","description":"Note content"}},"required":["title","content"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val content = args["content"]?.jsonPrimitive?.content ?: ""
                val safeFileName = title.replace(Regex("""[^\w\s.-]"""), "_").trim() + ".md"
                val file = resolveFile(safeFileName, request)
                file.parentFile?.mkdirs()
                file.writeText("# $title\n\n$content")
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "create_note_file",
                    success = true,
                    outputText = "Note created: $safeFileName"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    fun getAll(): List<Tool> = listOf(readFileTool, writeFileTool, listFilesTool, searchFilesTool, createNoteFileTool)

    private fun resolveFile(path: String, request: ToolExecutionRequest): File {
        val base = request.appContext.filesDir
        val normalizedPath = path.trimStart('/')
        return File(base, normalizedPath)
    }

    private fun error(request: ToolExecutionRequest, code: String, message: String) = ToolResult(
        toolCallId = request.toolCallId,
        name = request.toolName,
        success = false,
        errorCode = code,
        outputText = message
    )
}
