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
    fun speechSegmentsKeepExactTextAndSourceOffsets() {
        val source = "Salut.  Voici BOZER !\nOn avance."
        val segments = BozerSpeechText.segments(source, maxCharacters = 40)

        assertEquals(listOf("Salut.", "Voici BOZER !", "On avance."), segments.map { it.text })
        segments.forEach { segment ->
            assertEquals(segment.text, source.substring(segment.start, segment.endExclusive))
        }
        assertTrue(segments.zipWithNext().all { (first, second) ->
            first.endExclusive <= second.start
        })
    }

    @Test
    fun longSpeechSegmentsStayWithinTheConfiguredLimit() {
        val source = "BOZER avance avec méthode et simplicité. ".repeat(8)
        val segments = BozerSpeechText.segments(source, maxCharacters = 36)

        assertTrue(segments.size > 1)
        assertTrue(segments.all { it.text.length <= 36 })
        segments.forEach { segment ->
            assertEquals(segment.text, source.substring(segment.start, segment.endExclusive))
        }
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
