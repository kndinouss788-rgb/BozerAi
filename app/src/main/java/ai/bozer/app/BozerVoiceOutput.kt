package ai.bozer.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Temporary Android system-TTS adapter for development and accessibility.
 *
 * This is NOT BOZER's original trained voice: Android chooses the installed
 * voice and may use its own engine/network settings. The final BOZER TTS
 * implementation will replace this adapter behind BozerSpeechEngine.
 */
class BozerVoiceOutput(context: Context) :
    BozerSpeechEngine,
    TextToSpeech.OnInitListener {

    private val engine = TextToSpeech(context.applicationContext, this)

    @Volatile private var ready = false
    @Volatile private var released = false
    private var pendingText: String? = null
    private var pendingUtteranceId: String? = null
    private var activeUtteranceId: String? = null

    @Synchronized
    override fun onInit(status: Int) {
        if (released || status != TextToSpeech.SUCCESS) {
            ready = false
            return
        }
        val languageStatus = engine.setLanguage(Locale.forLanguageTag("fr-FR"))
        ready = languageStatus >= TextToSpeech.LANG_AVAILABLE
        if (!ready) return

        val pending = pendingText
        val pendingId = pendingUtteranceId
        pendingText = null
        pendingUtteranceId = null
        if (!pending.isNullOrBlank()) speak(pending, pendingId ?: "bozer_reply")
    }

    @Synchronized
    override fun speak(text: String, utteranceId: String) {
        if (released) return
        val clean = text.trim().take(1800)
        if (clean.isBlank()) return
        if (!ready) {
            pendingText = clean
            pendingUtteranceId = utteranceId
            return
        }
        activeUtteranceId = utteranceId
        // QUEUE_FLUSH ensures one current BOZER utterance; newer speech replaces older speech.
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    @Synchronized
    override fun stop(utteranceId: String?) {
        val matchesPending = utteranceId == null || pendingUtteranceId == utteranceId
        if (matchesPending) {
            pendingText = null
            pendingUtteranceId = null
        }
        val matchesActive = utteranceId == null || activeUtteranceId == utteranceId
        if (ready && matchesActive) {
            engine.stop()
            activeUtteranceId = null
        }
    }

    override fun isReady(): Boolean = ready && !released

    @Synchronized
    override fun release() {
        if (released) return
        pendingText = null
        pendingUtteranceId = null
        activeUtteranceId = null
        ready = false
        released = true
        engine.stop()
        engine.shutdown()
    }

    /** Kept temporarily for compatibility with older callers. */
    @Deprecated("Use release()")
    fun shutdown() = release()
}
