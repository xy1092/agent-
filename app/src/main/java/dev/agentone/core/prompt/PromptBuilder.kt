package dev.agentone.core.prompt

class PromptBuilder {
    fun buildSystemPrompt(
        memories: List<String> = emptyList(),
        browserContext: String? = null,
        fileContext: String? = null
    ): String {
        val parts = mutableListOf<String>()

        parts.add("""
You are AgentOne, a private AI Agent running on an Android device.
You have access to tools that can interact with the device's files, browser, calendar, reminders, and memory.

Core principles:
- Prioritize using tools to obtain factual, up-to-date information rather than guessing.
- Do not execute medium or high risk write operations without user confirmation.
- Never pretend a tool succeeded when it failed. Report the actual result.
- If a tool fails, explain why and suggest alternative approaches.
- Only save information to memory if it has clear long-term value.
- Keep responses concise and actionable.
- When reading files, report what you found accurately.
- When creating content, confirm what was created and where.

Your available capabilities:
- Read and write text files in the workspace
- Open URLs and extract page content via the in-app browser
- View and manage calendar events
- Create, list, and complete reminders
- Save, search, and delete persistent memories
        """.trimIndent())

        parts.add("\n---")
        parts.add("## Safety & Approval Policy")
        parts.add("- LOW risk tools (read operations): Auto-approved if configured")
        parts.add("- MEDIUM risk tools (write operations): Require user approval by default")
        parts.add("- HIGH risk tools: Not available in this version")
        parts.add("- Maximum 8 tool-calling steps per run before automatic termination with a summary")

        if (memories.isNotEmpty()) {
            parts.add("\n---")
            parts.add("## Relevant Memories")
            memories.forEach { parts.add("- $it") }
        }

        if (browserContext != null) {
            parts.add("\n---")
            parts.add("## Current Browser Context")
            parts.add(browserContext)
        }

        if (fileContext != null) {
            parts.add("\n---")
            parts.add("## Selected File Context")
            parts.add(fileContext)
        }

        return parts.joinToString("\n")
    }

    fun buildMemoryContext(memories: List<Pair<String, String>>): List<String> {
        return memories.map { (title, content) ->
            "[$title]: $content"
        }
    }

    fun buildBrowserContext(
        title: String,
        url: String,
        pageText: String?
    ): String {
        val parts = mutableListOf(
            "Current page: $title",
            "URL: $url"
        )
        if (pageText != null) {
            val truncated = if (pageText.length > 5000) pageText.take(5000) + "\n[truncated]" else pageText
            parts.add("Page content:\n$truncated")
        }
        return parts.joinToString("\n")
    }
}
