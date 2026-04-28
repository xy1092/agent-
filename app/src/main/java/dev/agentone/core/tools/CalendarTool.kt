package dev.agentone.core.tools

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarTool(private val contentResolver: ContentResolver) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val permissionDeniedError = ToolResult(
        toolCallId = "", name = "", success = false,
        errorCode = "CALENDAR_PERMISSION_DENIED", outputText = "Calendar permission not granted"
    )

    val listEventsTool = object : Tool {
        override val definition = ToolDefinition(
            name = "list_calendar_events",
            description = "List calendar events within a given time range",
            inputSchemaJson = """{"type":"object","properties":{"daysAhead":{"type":"integer","description":"Number of days ahead to list (default: 7)"}},"required":[]}""",
            riskLevel = "LOW"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val daysAhead = args["daysAhead"]?.jsonPrimitive?.content?.toIntOrNull() ?: 7

                val now = System.currentTimeMillis()
                val end = now + daysAhead * 24 * 60 * 60 * 1000L

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                )

                val cursor = contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                    arrayOf(now.toString(), end.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )

                val events = mutableListOf<String>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val title = it.getString(1) ?: "Untitled"
                        val startMs = it.getLong(2)
                        val endMs = it.getLong(3)
                        val location = it.getString(4) ?: ""
                        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        val startStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()).format(fmt)
                        val endStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault()).format(fmt)
                        events.add("$title | $startStr - $endStr${if (location.isNotEmpty()) " | $location" else ""}")
                    }
                }

                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "list_calendar_events",
                    success = true,
                    outputText = if (events.isEmpty()) "No events in the next $daysAhead days" else events.joinToString("\n")
                )
            } catch (e: SecurityException) {
                return error(request, "CALENDAR_PERMISSION_DENIED", "Calendar permission denied")
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val createEventTool = object : Tool {
        override val definition = ToolDefinition(
            name = "create_calendar_event",
            description = "Create a new calendar event",
            inputSchemaJson = """{"type":"object","properties":{"title":{"type":"string","description":"Event title"},"startTime":{"type":"string","description":"Start time in ISO format (yyyy-MM-ddTHH:mm)"},"endTime":{"type":"string","description":"End time in ISO format"},"location":{"type":"string","description":"Optional location"},"description":{"type":"string","description":"Optional description"}},"required":["title","startTime","endTime"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val title = args["title"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing title")
                val startStr = args["startTime"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing startTime")
                val endStr = args["endTime"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing endTime")
                val location = args["location"]?.jsonPrimitive?.content ?: ""
                val description = args["description"]?.jsonPrimitive?.content ?: ""

                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                val startMs = LocalDateTime.parse(startStr, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs = LocalDateTime.parse(endStr, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val values = ContentValues().apply {
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.CALENDAR_ID, 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                }

                val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "create_calendar_event",
                    success = true,
                    outputText = "Event created: $title (URI: $uri)"
                )
            } catch (e: SecurityException) {
                return error(request, "CALENDAR_PERMISSION_DENIED", "Calendar permission denied")
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    val updateEventTool = object : Tool {
        override val definition = ToolDefinition(
            name = "update_calendar_event",
            description = "Update an existing calendar event by title match (first match)",
            inputSchemaJson = """{"type":"object","properties":{"searchTitle":{"type":"string","description":"Title of event to find"},"newTitle":{"type":"string","description":"New title"},"newStartTime":{"type":"string","description":"New start time (ISO format)"},"newEndTime":{"type":"string","description":"New end time (ISO format)"}},"required":["searchTitle"]}""",
            riskLevel = "MEDIUM"
        )

        override suspend fun execute(request: ToolExecutionRequest): ToolResult {
            try {
                val args = json.parseToJsonElement(request.argumentsJson).jsonObject
                val searchTitle = args["searchTitle"]?.jsonPrimitive?.content ?: return error(request, "INVALID_ARGUMENTS", "Missing searchTitle")

                val values = ContentValues()
                args["newTitle"]?.jsonPrimitive?.content?.let { values.put(CalendarContract.Events.TITLE, it) }
                args["newStartTime"]?.jsonPrimitive?.content?.let {
                    val ms = LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    values.put(CalendarContract.Events.DTSTART, ms)
                }
                args["newEndTime"]?.jsonPrimitive?.content?.let {
                    val ms = LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    values.put(CalendarContract.Events.DTEND, ms)
                }

                val updated = contentResolver.update(
                    CalendarContract.Events.CONTENT_URI,
                    values,
                    "${CalendarContract.Events.TITLE} LIKE ?",
                    arrayOf("%$searchTitle%")
                )

                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = "update_calendar_event",
                    success = true,
                    outputText = "Updated $updated event(s) matching '$searchTitle'"
                )
            } catch (e: SecurityException) {
                return error(request, "CALENDAR_PERMISSION_DENIED", "Calendar permission denied")
            } catch (e: Exception) {
                return error(request, "INTERNAL_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    fun getAll(): List<Tool> = listOf(listEventsTool, createEventTool, updateEventTool)

    private fun error(request: ToolExecutionRequest, code: String, message: String) = ToolResult(
        toolCallId = request.toolCallId,
        name = request.toolName,
        success = false,
        errorCode = code,
        outputText = message
    )
}
