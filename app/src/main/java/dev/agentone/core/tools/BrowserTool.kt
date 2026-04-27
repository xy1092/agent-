package dev.agentone.core.tools

import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BrowserTool(
    private val pageStateProvider: PageStateProvider?
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val openUrlTool = object : Tool {
        override val definition = ToolDefinition(
            name = "open_url",
            description = "Open a URL in the in-app browser",
            inputSchemaJson = """{"type":"object","properties":{"url":{"type":"string","description":"URL to open"}},"required":["url"]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            val args = json.parseToJsonElement(request.argumentsJson).jsonObject
            val url = args["url"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing url")
            pageStateProvider?.navigateToUrl(url)
            return ToolResult(
                toolCallId = request.toolCallId,
                name = "open_url",
                success = true,
                outputText = "Opened URL: $url"
            )
        }
    }

    val extractPageTextTool = object : Tool {
        override val definition = ToolDefinition(
            name = "extract_page_text",
            description = "Extract visible text content from the current page in the in-app browser",
            inputSchemaJson = """{"type":"object","properties":{},"required":[]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            val text = pageStateProvider?.getPageText() ?: return error(request, "PAGE_NOT_READY", "No page loaded")
            val truncated = if (text.length > 20_000) text.take(20_000) + "\n\n[Truncated at 20000 chars]" else text
            return ToolResult(
                toolCallId = request.toolCallId,
                name = "extract_page_text",
                success = true,
                outputText = truncated
            )
        }
    }

    val capturePageTitleAndUrlTool = object : Tool {
        override val definition = ToolDefinition(
            name = "capture_page_title_and_url",
            description = "Get the current page title and URL",
            inputSchemaJson = """{"type":"object","properties":{},"required":[]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            val info = pageStateProvider?.getPageInfo() ?: return error(request, "PAGE_NOT_READY", "No page loaded")
            return ToolResult(
                toolCallId = request.toolCallId,
                name = "capture_page_title_and_url",
                success = true,
                outputText = "Title: ${info.first}\nURL: ${info.second}"
            )
        }
    }

    fun getAll(): List<Tool> = listOf(openUrlTool, extractPageTextTool, capturePageTitleAndUrlTool)

    private fun error(request: ToolExecutionRequest, code: String, message: String) = ToolResult(
        toolCallId = request.toolCallId,
        name = request.toolName,
        success = false,
        errorCode = code,
        outputText = message
    )

    interface PageStateProvider {
        fun getPageText(): String?
        fun getPageInfo(): Pair<String, String>?
        fun navigateToUrl(url: String)
    }
}
