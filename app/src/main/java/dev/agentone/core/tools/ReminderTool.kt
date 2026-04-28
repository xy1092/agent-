package dev.agentone.core.tools

import dev.agentone.core.database.ReminderDao
import dev.agentone.core.model.ReminderEntity
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReminderTool(private val reminderDao: ReminderDao) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val createReminderTool = object : Tool {
        override val definition = ToolDefinition(
            name = "create_reminder",
            description = "Create a new reminder with an optional due time",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Reminder title"},"note":{"type":"string","description":"Optional note"},"dueInMinutes":{"type":"integer","description":"Due in N minutes from now"}},"required":["title"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val note = args["note"]?.jsonPrimitive?.content
                val dueInMinutes = args["dueInMinutes"]?.jsonPrimitive?.content?.toIntOrNull()
                val dueAt = dueInMinutes?.let { System.currentTimeMillis() + it * 60 * 1000L }

                val reminder = ReminderEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    note = note,
                    dueAt = dueAt
                )
                reminderDao.upsert(reminder)
                val dueStr = dueAt?.let { " (due in $dueInMinutes min)" } ?: ""
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "create_reminder",
                    success = true,
                    outputText = "Reminder created: $title$dueStr"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val listRemindersTool = object : Tool {
        override val definition = ToolDefinition(
            name = "list_reminders",
            description = "List all pending reminders",
            inputSchemaJson = """{"type":"object","properties":{},"required":[]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val reminders = reminderDao.observePending().first()
                val output = if (reminders.isEmpty()) "No pending reminders" else {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    reminders.joinToString("\n") { r ->
                        val due = r.dueAt?.let { " | Due: ${fmt.format(Date(it))}" } ?: ""
                        "- ${r.title}$due${r.note?.let { " | ${it.take(50)}" } ?: ""}"
                    }
                }
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "list_reminders",
                    success = true,
                    outputText = output
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val completeReminderTool = object : Tool {
        override val definition = ToolDefinition(
            name = "complete_reminder",
            description = "Mark a reminder as completed by title match",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Title of reminder to complete"}},"required":["title"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val all = reminderDao.observeAll().first()
                val match = all.find { it.title.contains(title, ignoreCase = true) }
                if (match == null) {
                    return ToolResult(
                        toolCallId = request.toolCallId,
                        name = "complete_reminder",
                        success = false,
                        outputText = "No reminder found matching '$title'"
                    )
                }
                reminderDao.setCompleted(match.id, true)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "complete_reminder",
                    success = true,
                    outputText = "Reminder completed: ${match.title}"
                )
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    fun getAll(): List<Tool> = listOf(createReminderTool, listRemindersTool, completeReminderTool)

    private fun error(request: ToolExecutionRequest, code: String, message: String) = ToolResult(
        toolCallId = request.toolCallId,
        name = request.toolName,
        success = false,
        errorCode = code,
        outputText = message
    )
}
