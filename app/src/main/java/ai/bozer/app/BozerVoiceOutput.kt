package ai.bozer.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Local speech output; it does not call a cloud voice API. */
class BozerVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        engine.language = Locale.forLanguageTag("fr-FR")
        pendingText?.let { speak(it) }
        pendingText = null
    }

    fun speak(text: String) {
        val clean = text.trim().take(1800)
        if (clean.isBlank()) return
        if (!ready) {
            pendingText = clean
            return
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "bozer_reply")
    }

    fun stop() {
        engine.stop()
        pendingText = null
    }

    fun shutdown() {
        stop()
        engine.shutdown()
        ready = false
    }
}
