package dev.agentone

import dev.agentone.core.tools.RiskLevel
import dev.agentone.core.tools.Tool
import dev.agentone.core.tools.ToolExecutionRequest
import dev.agentone.core.tools.ToolRegistry
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRuntimeTest {

    @Test
    fun `tool registry registers and retrieves tools`() {
        val registry = ToolRegistry()
        val tool = createTestTool("test_tool", "LOW")
        registry.register(tool)

        assertNotNull(registry.get("test_tool"))
        assertEquals(1, registry.listAll().size)
    }

    @Test
    fun `tool registry filters by risk level`() {
        val registry = ToolRegistry()
        registry.register(createTestTool("low_tool", "LOW"))
        registry.register(createTestTool("med_tool", "MEDIUM"))
        registry.register(createTestTool("high_tool", "HIGH"))

        assertEquals(1, registry.listByRisk(RiskLevel.LOW).size)
        assertEquals(2, registry.listByRisk(RiskLevel.MEDIUM).size)
        assertEquals(3, registry.listByRisk(RiskLevel.HIGH).size)
    }

    @Test
    fun `tool returns error for TOOL_NOT_FOUND`() {
        val registry = ToolRegistry()
        assertTrue(registry.get("nonexistent") == null)
    }

    @Test
    fun `tool definitions are accessible`() {
        val registry = ToolRegistry()
        registry.register(createTestTool("my_tool", "LOW"))

        val defs = registry.getDefinitions()
        assertEquals(1, defs.size)
        assertEquals("my_tool", defs[0].name)
    }

    @Test
    fun `risk level enum ordering is correct`() {
        assertTrue(RiskLevel.LOW.ordinal < RiskLevel.MEDIUM.ordinal)
        assertTrue(RiskLevel.MEDIUM.ordinal < RiskLevel.HIGH.ordinal)
    }

    private fun createTestTool(name: String, risk: String): Tool {
        return object : Tool {
            override val definition = ToolDefinition(
                name = name,
                description = "Test tool",
                inputSchemaJson = """{"type":"object","properties":{},"required":[]}""",
                riskLevel = risk
            )

            override suspend fun execute(request: ToolExecutionRequest): ToolResult {
                return ToolResult(
                    toolCallId = request.toolCallId,
                    name = name,
                    success = true,
                    outputText = "OK"
                )
            }
        }
    }
}
