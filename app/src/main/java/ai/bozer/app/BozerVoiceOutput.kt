package ai.bozer.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Local Android speech output; no cloud voice service or audio recording is used. */
class BozerVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)

    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (closed || status != TextToSpeech.SUCCESS) return

        val french = engine.setLanguage(Locale.forLanguageTag("fr-FR"))
        ready = french != TextToSpeech.LANG_MISSING_DATA &&
            french != TextToSpeech.LANG_NOT_SUPPORTED

        if (!ready) {
            val fallback = engine.setLanguage(Locale.FRENCH)
            ready = fallback != TextToSpeech.LANG_MISSING_DATA &&
                fallback != TextToSpeech.LANG_NOT_SUPPORTED
        }

        if (ready) {
            val queued = pendingText
            pendingText = null
            if (queued != null) speak(queued)
        }
    }

    /** Returns true when Android accepted the utterance for playback. */
    fun speak(text: String): Boolean {
        if (closed) return false
        val clean = text.trim().take(MAX_SPOKEN_CHARS)
        if (clean.isBlank()) return false
        if (!ready) {
            pendingText = clean
            return false
        }
        return engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) ==
            TextToSpeech.SUCCESS
    }

    fun isReady(): Boolean = ready && !closed

    fun stop() {
        pendingText = null
        if (ready && !closed) engine.stop()
    }

    fun shutdown() {
        if (closed) return
        closed = true
        ready = false
        pendingText = null
        engine.stop()
        engine.shutdown()
    }

    companion object {
        private const val MAX_SPOKEN_CHARS = 1800
        private const val UTTERANCE_ID = "bozer_reply"
    }
}
