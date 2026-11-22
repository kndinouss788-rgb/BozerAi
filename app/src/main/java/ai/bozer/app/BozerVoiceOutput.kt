package ai.bozer.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Local Android speech output with character-range callbacks.
 * TTS is entirely device-side; BOZER does not upload text or record audio here.
 */
class BozerVoiceOutput(context: Context) : TextToSpeech.OnInitListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ids = AtomicLong(0L)
    private val engine = TextToSpeech(context.applicationContext, this)

    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var pending: PendingUtterance? = null
    @Volatile private var active: PendingUtterance? = null
    @Volatile private var activeId: String? = null

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val callbacks = activeFor(utteranceId) ?: return
                mainHandler.post { callbacks.onStart() }
            }

            override fun onDone(utteranceId: String?) {
                val callbacks = activeFor(utteranceId) ?: return
                clearActive(utteranceId)
                mainHandler.post { callbacks.onDone() }
            }

            @Deprecated("Deprecated by Android; retained for platform compatibility.")
            override fun onError(utteranceId: String?) {
                val callbacks = activeFor(utteranceId) ?: return
                clearActive(utteranceId)
                mainHandler.post { callbacks.onError() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val callbacks = activeFor(utteranceId) ?: return
                clearActive(utteranceId)
                mainHandler.post { callbacks.onError() }
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                val callbacks = activeFor(utteranceId) ?: return
                mainHandler.post { callbacks.onRange(start, (end - start).coerceAtLeast(0)) }
            }
        })
    }

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
            val queued = pending
            pending = null
            if (queued != null) start(queued)
        }
    }

    /**
     * Returns true if the utterance was queued or accepted by Android.
     * onRange receives character offsets for precise visual synchronization.
     */
    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onRange: (start: Int, length: Int) -> Unit = { _, _ -> },
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ): Boolean {
        if (closed) return false
        val clean = text.trim().take(MAX_SPOKEN_CHARS)
        if (clean.isBlank()) return false
        val request = PendingUtterance(
            id = "bozer_reply_${ids.incrementAndGet()}",
            text = clean,
            onStart = onStart,
            onRange = onRange,
            onDone = onDone,
            onError = onError
        )
        if (!ready) {
            pending = request
            return true
        }
        return start(request)
    }

    private fun start(request: PendingUtterance): Boolean {
        if (closed || !ready) {
            pending = request
            return !closed
        }
        active = request
        activeId = request.id
        val accepted = engine.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            request.id
        ) == TextToSpeech.SUCCESS
        if (!accepted) {
            clearActive(request.id)
            mainHandler.post { request.onError() }
        }
        return accepted
    }

    private fun activeFor(id: String?): PendingUtterance? =
        if (id != null && id == activeId) active else null

    private fun clearActive(id: String?) {
        if (id != null && id == activeId) {
            active = null
            activeId = null
        }
    }

    fun isReady(): Boolean = ready && !closed

    fun isSpeaking(): Boolean = ready && !closed && engine.isSpeaking

    fun stop() {
        pending = null
        val previous = active
        active = null
        activeId = null
        if (ready && !closed) engine.stop()
        if (previous != null) mainHandler.post { previous.onDone() }
    }

    fun shutdown() {
        if (closed) return
        closed = true
        ready = false
        pending = null
        active = null
        activeId = null
        engine.stop()
        engine.shutdown()
    }

    private data class PendingUtterance(
        val id: String,
        val text: String,
        val onStart: () -> Unit,
        val onRange: (Int, Int) -> Unit,
        val onDone: () -> Unit,
        val onError: () -> Unit
    )

    companion object {
        private const val MAX_SPOKEN_CHARS = 1800
    }
}
