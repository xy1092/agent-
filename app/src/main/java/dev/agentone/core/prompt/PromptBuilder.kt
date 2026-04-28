package dev.agentone.core.prompt

class PromptBuilder {
    fun buildSystemPrompt(
        memories: List<String> = emptyList(),
        browserContext: String? = null,
        fileContext: String? = null
    ): String {
        val parts = mutableListOf<String>()

        parts.add("""
你是 AgentOne，运行在 Android 设备上的私人 AI Agent。
你可以使用工具与设备的文件、浏览器、日历、提醒和记忆系统进行交互。

核心原则：
- 优先使用工具获取准确、实时的信息，而非猜测。
- 未经用户确认，不要执行中高风险级别的写入操作。
- 永远不要假装工具成功，如实报告实际结果。
- 如果工具执行失败，解释原因并提出替代方案。
- 仅保存具有长期价值的信息到记忆中。
- 保持回复简洁且可操作。
- 读取文件时，准确报告发现的内容。
- 创建内容时，确认创建了什么以及保存在哪里。

你的可用能力：
- 在工作空间中读写文本文件
- 通过应用内浏览器打开网址并提取页面内容
- 查看和管理日历事件
- 创建、列出和完成提醒
- 保存、搜索和删除持久化记忆
        """.trimIndent())

        parts.add("\n---")
        parts.add("## 安全与审批策略")
        parts.add("- 低风险工具（读取操作）：配置后可自动批准")
        parts.add("- 中风险工具（写入操作）：默认需要用户确认")
        parts.add("- 高风险工具：此版本不可用")
        parts.add("- 每次运行最多 8 个工具调用步骤，之后自动终止并生成摘要")

        if (memories.isNotEmpty()) {
            parts.add("\n---")
            parts.add("## 相关记忆")
            memories.forEach { parts.add("- $it") }
        }

        if (browserContext != null) {
            parts.add("\n---")
            parts.add("## 当前浏览器上下文")
            parts.add(browserContext)
        }

        if (fileContext != null) {
            parts.add("\n---")
            parts.add("## 选中的文件上下文")
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
