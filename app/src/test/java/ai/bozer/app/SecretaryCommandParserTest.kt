package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretaryCommandParserTest {
    @Test
    fun parsesFrenchAddTaskWithAccents() {
        assertEquals(
            SecretaryCommand.Add("Appeler le garage demain"),
            SecretaryCommandParser.parse("BOZER, ajoute une tâche Appeler le garage demain")
        )
    }

    @Test
    fun parsesTaskCompletionAndDeletion() {
        assertEquals(
            SecretaryCommand.Complete("Faire les courses"),
            SecretaryCommandParser.parse("termine la tâche Faire les courses")
        )
        assertEquals(
            SecretaryCommand.Delete("Faire les courses"),
            SecretaryCommandParser.parse("supprime la tâche Faire les courses")
        )
    }

    @Test
    fun listsTasksAndIgnoresUnrelatedChat() {
        assertEquals(SecretaryCommand.ListOpen, SecretaryCommandParser.parse("montre mes tâches"))
        assertNull(SecretaryCommandParser.parse("Salut BOZER, comment vas-tu ?"))
        assertNull(SecretaryCommandParser.parse("rappelle-moi demain"))
    }
}
