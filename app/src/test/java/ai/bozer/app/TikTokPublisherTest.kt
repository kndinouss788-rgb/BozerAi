package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TikTokPublisherTest {
    @Test
    fun buildsCaptionAndNormalizesHashtags() {
        assertEquals(
            "Présentation de BOZER\n\n#bozer #ia #innovation",
            TikTokPublisher.buildCaption("Présentation de BOZER", "#bozer ia, #innovation")
        )
    }

    @Test
    fun removesDuplicateHashtagsAndAllowsEmptyDescription() {
        assertEquals(
            "#bozer #IA",
            TikTokPublisher.buildCaption("", "#bozer bozer IA")
        )
    }
}
