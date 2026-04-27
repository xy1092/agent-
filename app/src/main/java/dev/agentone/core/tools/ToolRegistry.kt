package dev.agentone.core.tools

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.definition.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun listAll(): List<Tool> = tools.values.toList()

    fun listByRisk(maxRisk: RiskLevel): List<Tool> = tools.values.filter {
        val risk = try { RiskLevel.valueOf(it.definition.riskLevel.uppercase()) } catch (_: Exception) { RiskLevel.LOW }
        risk.ordinal <= maxRisk.ordinal
    }

    fun getDefinitions(): List<dev.agentone.core.providers.ToolDefinition> = tools.values.map { it.definition }
}
