package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BozerSpeechTextTest {
    @Test
    fun splitsLongResponsesWithoutDroppingWordsOrPunctuation() {
        val source = (
            "BOZER répond naturellement, sans couper les phrases au hasard. " +
            "Cette réponse assez longue doit être découpée proprement, " +
            "mais chaque mot et chaque signe doit rester présent !"
        )
        val parts = BozerSpeechText.chunks(source, maxCharacters = 36)

        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 36 })
        assertEquals(source, parts.joinToString(" "))
    }

    @Test
    fun emptyTextProducesNoUtterances() {
        assertTrue(BozerSpeechText.chunks("   ").isEmpty())
    }

    @Test
    fun oversizedSingleWordIsSplitRatherThanDiscarded() {
        val word = "A".repeat(65)
        val parts = BozerSpeechText.chunks(word, maxCharacters = 20)

        assertEquals(word, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 20 })
    }
}
