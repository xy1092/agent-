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
        val response = generateFakeResponse(lastUserMsg, request.tools, request.messages)
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
        tools: List<ToolDefinition>?,
        history: List<UnifiedMessage>
    ): Pair<UnifiedMessage, List<ToolCall>> {
        val msg = userMessage.lowercase()
        val hasTool = { name: String -> tools?.any { it.name == name } == true }

        // --- File reading ---
        if (hasTool("read_file") && (msg.contains("read") || msg.contains("查看") || msg.contains("读") || msg.contains("看")) &&
            (msg.contains("file") || msg.contains("txt") || msg.contains("文件") || msg.contains("note"))) {
            val fileName = Regex("""['"]?([\w./-]+\.\w+)['"]?""").find(userMessage)?.groupValues?.get(1)
                ?: Regex("""['"]?([一-鿿\w./-]+\.\w+)['"]?""").find(userMessage)?.groupValues?.get(1)
                ?: "unknown.txt"
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "read_file",
                argumentsJson = """{"path":"$fileName"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "让我来读取 $fileName ...", listOf(toolCall)) to listOf(toolCall)
        }

        // --- File writing / note creation ---
        if (hasTool("write_file") && (msg.contains("write") || msg.contains("save") || msg.contains("create") ||
                msg.contains("写") || msg.contains("创建") || msg.contains("保存") || msg.contains("新建") || msg.contains("记录"))) {
            if (msg.contains("note") || msg.contains("笔记") || msg.contains("备忘")) {
                val toolCall = ToolCall(
                    id = "fake-tc-${System.currentTimeMillis()}",
                    name = "create_note_file",
                    argumentsJson = """{"title":"New Note","content":"$userMessage"}"""
                )
                return UnifiedMessage(MessageRole.ASSISTANT, "我来创建这个笔记。", listOf(toolCall)) to listOf(toolCall)
            }
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "write_file",
                argumentsJson = """{"path":"note.txt","content":"$userMessage"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "好的，我来写入文件。", listOf(toolCall)) to listOf(toolCall)
        }

        // --- File listing ---
        if (hasTool("list_files") && (msg.contains("list") || msg.contains("show") || msg.contains("列出") || msg.contains("显示") || msg.contains("看")) &&
            (msg.contains("file") || msg.contains("director") || msg.contains("文件") || msg.contains("目录") || msg.contains("folder"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "list_files",
                argumentsJson = """{"path":"."}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "让我列出当前目录的文件。", listOf(toolCall)) to listOf(toolCall)
        }

        // --- Reminders ---
        if (hasTool("create_reminder") && (msg.contains("remind") || msg.contains("reminder") || msg.contains("提醒") || msg.contains("设置提醒"))) {
            val dueMin = Regex("""(\d+)\s*(min|minutes|分钟|小时)""").find(msg)?.groupValues?.get(1)?.toIntOrNull()
            val dueInMinutes = dueMin ?: if (msg.contains("小时")) 60 else 30
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "create_reminder",
                argumentsJson = """{"title":"${userMessage.take(50)}","note":"$userMessage","dueInMinutes":$dueInMinutes}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "已创建提醒（${dueInMinutes}分钟后触发）。", listOf(toolCall)) to listOf(toolCall)
        }

        if (hasTool("list_reminders") && (msg.contains("list") || msg.contains("show") || msg.contains("查看")) &&
            (msg.contains("reminder") || msg.contains("提醒"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "list_reminders",
                argumentsJson = """{}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "让我查看你的提醒列表。", listOf(toolCall)) to listOf(toolCall)
        }

        if (hasTool("complete_reminder") && (msg.contains("complete") || msg.contains("done") || msg.contains("完成") || msg.contains("标记"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "complete_reminder",
                argumentsJson = """{"title":"${userMessage.take(30)}"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "好的，标记该提醒为已完成。", listOf(toolCall)) to listOf(toolCall)
        }

        // --- Memory ---
        if (hasTool("save_memory") && (msg.contains("remember") || msg.contains("memory") || msg.contains("记忆") || msg.contains("记住") || msg.contains("保存这"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "save_memory",
                argumentsJson = """{"title":"记忆: ${userMessage.take(30)}","content":"$userMessage","tags":"agentone"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "已保存到记忆。", listOf(toolCall)) to listOf(toolCall)
        }

        if (hasTool("search_memory") && ((msg.contains("search") || msg.contains("find") || msg.contains("搜索") || msg.contains("查找")) &&
                (msg.contains("memory") || msg.contains("记忆")))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "search_memory",
                argumentsJson = """{"query":"${userMessage.take(30)}"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "正在搜索记忆...", listOf(toolCall)) to listOf(toolCall)
        }

        if (hasTool("delete_memory") && (msg.contains("delete") || msg.contains("forget") || msg.contains("删除") || msg.contains("忘记"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "delete_memory",
                argumentsJson = """{"title":"${userMessage.take(30)}"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "好的，删除相关记忆。", listOf(toolCall)) to listOf(toolCall)
        }

        // --- Browser ---
        if (hasTool("open_url") && (msg.contains("open") || msg.contains("browse") || msg.contains("http") || msg.contains("打开") || msg.contains("搜索"))) {
            val url = Regex("""(https?://\S+)""").find(userMessage)?.groupValues?.get(1) ?: "https://www.google.com/search?q=${userMessage.replace(" ", "+")}"
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "open_url",
                argumentsJson = """{"url":"$url"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "正在打开页面...", listOf(toolCall)) to listOf(toolCall)
        }

        // --- Calendar ---
        if (hasTool("list_calendar_events") && ((msg.contains("calendar") || msg.contains("日历") || msg.contains("日程")) &&
                (msg.contains("list") || msg.contains("show") || msg.contains("查看") || msg.contains("今天") || msg.contains("明天")))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "list_calendar_events",
                argumentsJson = """{"daysAhead":7}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "让我看看你的日历安排。", listOf(toolCall)) to listOf(toolCall)
        }

        if (hasTool("create_calendar_event") && (msg.contains("add") || msg.contains("schedule") || msg.contains("添加") || msg.contains("安排"))) {
            val toolCall = ToolCall(
                id = "fake-tc-${System.currentTimeMillis()}",
                name = "create_calendar_event",
                argumentsJson = """{"title":"${userMessage.take(40)}","startTime":"${System.currentTimeMillis() + 3600000}","endTime":"${System.currentTimeMillis() + 7200000}"}"""
            )
            return UnifiedMessage(MessageRole.ASSISTANT, "好的，添加日历事件。", listOf(toolCall)) to listOf(toolCall)
        }

        // --- Greeting / small talk ---
        if (msg in listOf("hi", "hello", "hey", "你好", "嗨", "早上好", "晚上好", "下午好") ||
            msg.startsWith("hi ") || msg.startsWith("hello ") || msg.startsWith("你好")) {
            return UnifiedMessage(MessageRole.ASSISTANT,
                "你好！我是 AgentOne，你的私人 AI 助手。\n\n我可以帮你：\n• 读写文件\n• 管理提醒事项\n• 搜索和保存记忆\n• 查看日历\n• 打开网页\n\n请告诉我你需要什么？") to emptyList()
        }

        if (msg in listOf("help", "帮助", "?") || msg.contains("what can you do") || msg.contains("你能做什么") || msg.contains("功能")) {
            return UnifiedMessage(MessageRole.ASSISTANT,
                "我是 AgentOne，运行在你的 Android 设备上。\n\n我可以使用以下工具：\n• read_file / write_file — 读写工作空间文件\n• list_files / search_files — 浏览文件\n• create_reminder / list_reminders — 管理提醒\n• save_memory / search_memory — 持久记忆\n• list_calendar_events — 查看日历\n• open_url — 打开网页\n\n直接告诉我你想做什么！") to emptyList()
        }

        // --- Generic response for unhandled input ---
        return UnifiedMessage(MessageRole.ASSISTANT,
            "收到：「$userMessage」\n\n这是一个模拟回复（FakeProvider）。要获得真实的 AI 回复，请在设置中配置 DeepSeek、OpenAI 或其他提供商的 API Key。\n\n你可以试试：\n• \"列出文件\" — 查看工作空间文件\n• \"创建提醒\" — 设置提醒\n• \"记住...\" — 保存到记忆\n• \"打开 example.com\" — 浏览网页") to emptyList()
    }
}
