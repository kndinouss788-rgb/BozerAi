package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretaryReminderPolicyTest {
    @Test
    fun futureOpenTaskNeedsReminder() {
        val task = SecretaryTask(
            title = "Préparer le dossier",
            dueAtMillis = 2_000L
        )
        assertTrue(SecretaryReminderPolicy.requiresReminder(task, 1_000L))
    }

    @Test
    fun completedTaskDoesNotKeepReminder() {
        val task = SecretaryTask(
            title = "Appeler le fournisseur",
            dueAtMillis = 2_000L,
            completed = true,
            reminderAlarmId = 12L
        )
        assertFalse(SecretaryReminderPolicy.requiresReminder(task, 1_000L))
    }

    @Test
    fun missingOrPastDueDateDoesNotCreateReminder() {
        val withoutDueDate = SecretaryTask(title = "Lire le document")
        val pastDueDate = SecretaryTask(title = "Envoyer le devis", dueAtMillis = 900L)

        assertFalse(SecretaryReminderPolicy.requiresReminder(withoutDueDate, 1_000L))
        assertFalse(SecretaryReminderPolicy.requiresReminder(pastDueDate, 1_000L))
    }

    @Test
    fun reminderLabelIsReadableAndBounded() {
        val task = SecretaryTask(title = "Réviser le projet")
        assertEquals("Échéance : Réviser le projet", SecretaryReminderPolicy.label(task))

        val longTitle = SecretaryTask(title = "A".repeat(140))
        assertEquals(120, SecretaryReminderPolicy.label(longTitle).length)
    }
}
