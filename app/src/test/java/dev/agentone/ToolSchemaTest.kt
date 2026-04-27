package dev.agentone

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSchemaTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val schemas = mapOf(
        "read_file" to """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""",
        "write_file" to """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
        "list_files" to """{"type":"object","properties":{"path":{"type":"string"}},"required":[]}""",
        "search_files" to """{"type":"object","properties":{"pattern":{"type":"string"}},"required":["pattern"]}""",
        "create_note_file" to """{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}""",
        "open_url" to """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
        "extract_page_text" to """{"type":"object","properties":{},"required":[]}""",
        "list_calendar_events" to """{"type":"object","properties":{"daysAhead":{"type":"integer"}},"required":[]}""",
        "create_calendar_event" to """{"type":"object","properties":{"title":{"type":"string"},"startTime":{"type":"string"},"endTime":{"type":"string"}},"required":["title","startTime","endTime"]}""",
        "create_reminder" to """{"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}""",
        "save_memory" to """{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}""",
        "search_memory" to """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}"""
    )

    @Test
    fun `all tool schemas are valid JSON`() {
        schemas.forEach { (name, schema) ->
            val parsed = json.parseToJsonElement(schema)
            assertTrue("Schema for $name is not valid JSON", parsed is JsonObject)
        }
    }

    @Test
    fun `all tool schemas have type object at root`() {
        schemas.forEach { (name, schema) ->
            val obj = json.parseToJsonElement(schema) as JsonObject
            assertEquals("object", obj["type"]?.toString()?.trim('"'))
        }
    }

    @Test
    fun `all tool schemas have properties`() {
        schemas.forEach { (name, schema) ->
            val obj = json.parseToJsonElement(schema) as JsonObject
            assertTrue("$name missing properties", obj.containsKey("properties"))
        }
    }

    @Test
    fun `all required fields are arrays`() {
        schemas.forEach { (name, schema) ->
            val obj = json.parseToJsonElement(schema) as JsonObject
            val required = obj["required"]
            if (required != null) {
                assertTrue("$name required is not an array", required.toString().startsWith("["))
            }
        }
    }
}
