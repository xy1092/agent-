package dev.agentone.core.tools

import dev.agentone.core.providers.ToolCall
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class ToolExecutionRequest(
    val toolCallId: String,
    val toolName: String,
    val argumentsJson: String,
    val sessionId: String,
    val runId: String,
    val appContext: ToolAppContext
)

data class ToolAppContext(
    val filesDir: java.io.File,
    val contentResolver: android.content.ContentResolver,
    val workspaceUri: String? = null
)

interface Tool {
    val definition: ToolDefinition
    suspend fun execute(request: ToolExecutionRequest): ToolResult
}
