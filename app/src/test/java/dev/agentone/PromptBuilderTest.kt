package dev.agentone

import dev.agentone.core.prompt.PromptBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun `system prompt contains core instructions`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("AgentOne"))
        assertTrue(prompt.contains("private AI Agent"))
        assertTrue(prompt.contains("tools"))
    }

    @Test
    fun `system prompt includes safety policy`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("LOW risk"))
        assertTrue(prompt.contains("MEDIUM risk"))
        assertTrue(prompt.contains("HIGH risk"))
    }

    @Test
    fun `system prompt includes max steps`() {
        val prompt = builder.buildSystemPrompt()
        assertTrue(prompt.contains("8"))
    }

    @Test
    fun `memories are injected when provided`() {
        val memories = listOf("[test]: remember this")
        val prompt = builder.buildSystemPrompt(memories = memories)
        assertTrue(prompt.contains("Relevant Memories"))
        assertTrue(prompt.contains("remember this"))
    }

    @Test
    fun `browser context is injected when provided`() {
        val prompt = builder.buildSystemPrompt(browserContext = "Page: Test")
        assertTrue(prompt.contains("Browser Context"))
        assertTrue(prompt.contains("Page: Test"))
    }

    @Test
    fun `build browser context produces correct format`() {
        val ctx = builder.buildBrowserContext("Test Title", "https://example.com", "Hello world")
        assertTrue(ctx.contains("Test Title"))
        assertTrue(ctx.contains("https://example.com"))
        assertTrue(ctx.contains("Hello world"))
    }

    @Test
    fun `no browser context section when null`() {
        val prompt = builder.buildSystemPrompt(browserContext = null)
        assertTrue(!prompt.contains("Browser Context"))
    }
}
