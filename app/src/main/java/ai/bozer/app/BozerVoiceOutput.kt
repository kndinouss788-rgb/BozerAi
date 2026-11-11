package ai.bozer.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Local speech output; no cloud voice API or recurring network cost. */
class BozerVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var pendingText: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        engine.language = Locale.forLanguageTag("fr-FR")
        val pending = pendingText
        pendingText = null
        if (!pending.isNullOrBlank()) speak(pending)
    }

    @Synchronized
    fun speak(text: String) {
        val clean = text.trim().take(1800)
        if (clean.isBlank()) return
        if (!ready) {
            pendingText = clean
            return
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "bozer_reply")
    }

    @Synchronized
    fun stop() {
        pendingText = null
        if (ready) engine.stop()
    }

    @Synchronized
    fun shutdown() {
        pendingText = null
        engine.stop()
        engine.shutdown()
        ready = false
    }
}
