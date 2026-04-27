package dev.agentone

import android.app.Application
import dev.agentone.core.agent.AgentRuntime
import dev.agentone.core.database.AgentOneDatabase
import dev.agentone.core.providers.ProviderRegistry
import dev.agentone.core.prompt.PromptBuilder
import dev.agentone.core.security.SecurityManager
import dev.agentone.core.tools.BrowserTool
import dev.agentone.core.tools.CalendarTool
import dev.agentone.core.tools.FileTool
import dev.agentone.core.tools.MemoryTool
import dev.agentone.core.tools.ReminderTool
import dev.agentone.core.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AgentOneApp : Application() {
    lateinit var database: AgentOneDatabase private set
    lateinit var securityManager: SecurityManager private set
    lateinit var providerRegistry: ProviderRegistry private set
    lateinit var toolRegistry: ToolRegistry private set
    lateinit var promptBuilder: PromptBuilder private set
    lateinit var browserTool: BrowserTool private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AgentOneDatabase.getInstance(this)
        securityManager = SecurityManager(this)
        providerRegistry = ProviderRegistry()
        promptBuilder = PromptBuilder()
        toolRegistry = ToolRegistry()

        val fileTool = FileTool(this)
        fileTool.getAll().forEach { toolRegistry.register(it) }

        browserTool = BrowserTool(null)
        browserTool.getAll().forEach { toolRegistry.register(it) }

        val calendarTool = CalendarTool(contentResolver)
        calendarTool.getAll().forEach { toolRegistry.register(it) }

        val reminderTool = ReminderTool(database.reminderDao())
        reminderTool.getAll().forEach { toolRegistry.register(it) }

        val memoryTool = MemoryTool(
            database.memoryEntryDao(),
            { securityManager.isMemoryEnabled() }
        )
        memoryTool.getAll().forEach { toolRegistry.register(it) }
    }

    companion object {
        lateinit var instance: AgentOneApp private set
    }
}
