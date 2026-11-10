package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretaryTaskTest {
    @Test
    fun taskKeepsDueDatePriorityAndCompletionState() {
        val task = SecretaryTask(
            title = "Préparer le rendez-vous",
            notes = "Apporter les documents",
            dueAtMillis = 1_800_000_000_000L,
            priority = SecretaryPriority.HIGH
        )
        assertEquals("Préparer le rendez-vous", task.title)
        assertEquals(SecretaryPriority.HIGH, task.priority)
        assertEquals(1_800_000_000_000L, task.dueAtMillis)
        assertFalse(task.completed)
        assertTrue(task.copy(completed = true).completed)
    }

    @Test
    fun priorityFallbackIsSafeForUnknownDatabaseValue() {
        assertEquals(SecretaryPriority.NORMAL, SecretaryPriority.fromLevel(99))
        assertEquals(SecretaryPriority.LOW, SecretaryPriority.fromLevel(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTaskTitleIsRejected() {
        SecretaryTask(title = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedTaskTitleIsRejected() {
        SecretaryTask(title = "x".repeat(141))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDueDateIsRejected() {
        SecretaryTask(title = "Appeler", dueAtMillis = -1L)
    }
}
