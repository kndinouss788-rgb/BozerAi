package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretaryCommandParserTest {
    @Test
    fun parsesAddTaskWithNaturalFrenchVariants() {
        assertEquals(
            SecretaryCommand.Add("acheter du pain"),
            SecretaryCommandParser.parse("Ajoute une tâche à ma liste : acheter du pain")
        )
        assertEquals(
            SecretaryCommand.Add("payer la facture"),
            SecretaryCommandParser.parse("BOZER, ajoute à ma liste payer la facture.")
        )
        assertEquals(
            SecretaryCommand.Add("appeler maman"),
            SecretaryCommandParser.parse("Note dans ma liste appeler maman!")
        )
    }

    @Test
    fun parsesCompleteDeleteAndListCommands() {
        assertEquals(
            SecretaryCommand.Complete("acheter du pain"),
            SecretaryCommandParser.parse("termine la tâche acheter du pain")
        )
        assertEquals(
            SecretaryCommand.Delete("payer la facture"),
            SecretaryCommandParser.parse("supprime la tâche payer la facture")
        )
        assertEquals(
            SecretaryCommand.ListOpen,
            SecretaryCommandParser.parse("montre mes tâches")
        )
    }

    @Test
    fun unrelatedConversationIsNotTreatedAsTaskCommand() {
        assertNull(SecretaryCommandParser.parse("Salut BOZER, comment vas-tu ?"))
        assertNull(SecretaryCommandParser.parse("Je dois peut-être acheter du pain"))
    }

    @Test
    fun taskListUsesRealLineBreaksAndReadableBullets() {
        val tasks = listOf(
            SecretaryTask(id = 1, title = "Acheter du pain"),
            SecretaryTask(id = 2, title = "Appeler maman")
        )
        assertEquals(
            "Voici tes tâches en attente :\n• Acheter du pain\n• Appeler maman",
            formatOpenSecretaryTasks(tasks)
        )
    }

    @Test
    fun emptyTaskListHasClearReply() {
        assertEquals(
            "Tu n'as aucune tâche en attente.",
            formatOpenSecretaryTasks(emptyList())
        )
    }
}
