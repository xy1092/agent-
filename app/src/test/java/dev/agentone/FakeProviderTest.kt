package dev.agentone

import dev.agentone.core.model.ProviderConfig
import dev.agentone.core.providers.ChatCompletionRequest
import dev.agentone.core.providers.FakeProvider
import dev.agentone.core.providers.MessageRole
import dev.agentone.core.providers.ToolDefinition
import dev.agentone.core.providers.UnifiedMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProviderTest {
    private val provider = FakeProvider()
    private val config = ProviderConfig(
        id = "test-fake",
        type = "FAKE",
        displayName = "Test",
        enabled = true
    )

    private val testTools = listOf(
        ToolDefinition("read_file", "Read a file", """{"type":"object","properties":{"path":{"type":"string"}}}""", "LOW"),
        ToolDefinition("write_file", "Write a file", """{"type":"object","properties":{"path":{"type":"string"}}}""", "MEDIUM"),
        ToolDefinition("save_memory", "Save memory", """{"type":"object","properties":{"title":{"type":"string"}}}""", "MEDIUM"),
        ToolDefinition("open_url", "Open URL", """{"type":"object","properties":{"url":{"type":"string"}}}""", "LOW")
    )

    @Test
    fun `returns text response for normal messages`() = runTest {
        val request = ChatCompletionRequest(
            providerType = "FAKE",
            model = "fake-v1",
            messages = listOf(UnifiedMessage(role = MessageRole.USER, content = "Hello"))
        )
        val result = provider.complete(request, config)
        assertNotNull(result.assistantMessage.content)
        assertTrue(result.requestedToolCalls.isEmpty())
    }

    @Test
    fun `triggers read_file tool for file read messages`() = runTest {
        val request = ChatCompletionRequest(
            providerType = "FAKE",
            model = "fake-v1",
            messages = listOf(UnifiedMessage(role = MessageRole.USER, content = "read the file test.txt")),
            tools = testTools
        )
        val result = provider.complete(request, config)
        assertEquals(1, result.requestedToolCalls.size)
        assertEquals("read_file", result.requestedToolCalls.first().name)
    }

    @Test
    fun `triggers write_file tool for write messages`() = runTest {
        val request = ChatCompletionRequest(
            providerType = "FAKE",
            model = "fake-v1",
            messages = listOf(UnifiedMessage(role = MessageRole.USER, content = "write a file")),
            tools = testTools
        )
        val result = provider.complete(request, config)
        assertEquals(1, result.requestedToolCalls.size)
        assertEquals("write_file", result.requestedToolCalls.first().name)
    }

    @Test
    fun `triggers save_memory for remember messages`() = runTest {
        val request = ChatCompletionRequest(
            providerType = "FAKE",
            model = "fake-v1",
            messages = listOf(UnifiedMessage(role = MessageRole.USER, content = "remember this")),
            tools = testTools
        )
        val result = provider.complete(request, config)
        assertEquals(1, result.requestedToolCalls.size)
        assertEquals("save_memory", result.requestedToolCalls.first().name)
    }

    @Test
    fun `triggers open_url for URLs`() = runTest {
        val request = ChatCompletionRequest(
            providerType = "FAKE",
            model = "fake-v1",
            messages = listOf(UnifiedMessage(role = MessageRole.USER, content = "open https://example.com")),
            tools = testTools
        )
        val result = provider.complete(request, config)
        assertEquals(1, result.requestedToolCalls.size)
        assertEquals("open_url", result.requestedToolCalls.first().name)
    }
}
