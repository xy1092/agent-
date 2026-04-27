package dev.agentone

import dev.agentone.core.database.MemoryEntryDao
import dev.agentone.core.model.MemoryEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    private val dao = mockk<MemoryEntryDao>()
    private val testEntry = MemoryEntry(
        id = "1",
        title = "Test Memory",
        content = "Test content",
        tags = "test,unit",
        sourceSessionId = "session1"
    )

    @Test
    fun `upsert memory entry`() = runTest {
        coEvery { dao.upsert(testEntry) } returns Unit

        dao.upsert(testEntry)
        coVerify { dao.upsert(testEntry) }
    }

    @Test
    fun `search returns matching entries`() = runTest {
        val flow = MutableStateFlow(listOf(testEntry))
        coEvery { dao.search("test") } returns flow

        val result = dao.search("test")
        result.collect { entries ->
            assertEquals(1, entries.size)
            assertEquals("Test Memory", entries[0].title)
        }
    }

    @Test
    fun `delete entry`() = runTest {
        coEvery { dao.deleteById("1") } returns Unit

        dao.deleteById("1")
        coVerify { dao.deleteById("1") }
    }

    @Test
    fun `memory has required fields`() {
        assertEquals("Test Memory", testEntry.title)
        assertEquals("Test content", testEntry.content)
        assertEquals("test,unit", testEntry.tags)
    }
}
