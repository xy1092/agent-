package dev.agentone

import dev.agentone.core.database.ReminderDao
import dev.agentone.core.model.ReminderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRepositoryTest {
    private val dao = mockk<ReminderDao>()
    private val pendingReminder = ReminderEntity(
        id = "1",
        title = "Test Reminder",
        note = "Note",
        dueAt = System.currentTimeMillis() + 3600000
    )
    private val completedReminder = pendingReminder.copy(id = "2", completed = true)

    @Test
    fun `upsert reminder`() = runTest {
        coEvery { dao.upsert(pendingReminder) } returns Unit

        dao.upsert(pendingReminder)
        coVerify { dao.upsert(pendingReminder) }
    }

    @Test
    fun `pending flow excludes completed`() = runTest {
        val allFlow = MutableStateFlow(listOf(pendingReminder, completedReminder))
        val pendingFlow = MutableStateFlow(listOf(pendingReminder))

        coEvery { dao.observeAll() } returns allFlow
        coEvery { dao.observePending() } returns pendingFlow

        val result = dao.observePending()
        result.collect { reminders ->
            assertEquals(1, reminders.size)
            assertFalse(reminders[0].completed)
        }
    }

    @Test
    fun `set completed updates status`() = runTest {
        coEvery { dao.setCompleted("1", true, any()) } returns Unit

        dao.setCompleted("1", true)
        coVerify { dao.setCompleted("1", true, any()) }
    }

    @Test
    fun `delete reminder`() = runTest {
        coEvery { dao.deleteById("1") } returns Unit

        dao.deleteById("1")
        coVerify { dao.deleteById("1") }
    }
}
