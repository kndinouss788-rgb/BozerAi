package ai.bozer.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Android system-TTS adapter. Sentence-start callbacks keep the visible answer
 * aligned with the speech actually being played.
 */
class BozerVoiceOutput(
    context: Context,
    private val onStatusChanged: (String) -> Unit = {}
) : BozerSpeechEngine, TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engine = TextToSpeech(context.applicationContext, this)

    @Volatile private var ready = false
    @Volatile private var released = false
    @Volatile private var currentStatus = "Initialisation du moteur vocal Android…"

    private var pendingText: String? = null
    private var pendingUtteranceId: String? = null
    private var pendingSegmentCallback: ((Int, Int) -> Unit)? = null
    private var pendingCompletedCallback: (() -> Unit)? = null
    private var activeUtteranceId: String? = null
    private val activeChunkIds = mutableSetOf<String>()
    private val activeChunkRanges = mutableMapOf<String, BozerSpeechText.Segment>()
    private var activeSegmentCallback: ((Int, Int) -> Unit)? = null
    private var activeCompletedCallback: (() -> Unit)? = null
    private var generation = 0L

    private fun publishStatus(message: String) {
        currentStatus = message
        mainHandler.post { if (!released) onStatusChanged(message) }
    }

    private fun isExplicitlyMasculine(voice: Voice): Boolean {
        val name = voice.name.lowercase(Locale.ROOT)
        return Regex("(^|[._ -])(male|masculin|homme|man|gilles|tom)([._ -]|$)")
            .containsMatchIn(name)
    }

    @Synchronized
    override fun onInit(status: Int) {
        if (released) return
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            publishStatus("Le moteur vocal Android n'a pas pu démarrer.")
            return
        }

        val languageStatus = engine.setLanguage(Locale.forLanguageTag("fr-FR"))
        if (languageStatus < TextToSpeech.LANG_AVAILABLE) {
            ready = false
            publishStatus(
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA) {
                    "Données vocales françaises absentes. Installe une voix française dans Android."
                } else {
                    "Aucune voix française compatible n'est disponible dans le moteur Android."
                }
            )
            return
        }

        val frenchVoices = engine.voices.orEmpty()
            .filter { it.locale.language.equals("fr", ignoreCase = true) }
        val selectedVoice = frenchVoices.sortedWith(
            compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { isExplicitlyMasculine(it) }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
        ).firstOrNull()

        if (selectedVoice != null) engine.voice = selectedVoice
        val masculineMetadata = selectedVoice?.let(::isExplicitlyMasculine) == true
        engine.setSpeechRate(0.96f)
        engine.setPitch(if (masculineMetadata) 1.0f else 0.93f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                val callbackAndRange = synchronized(this@BozerVoiceOutput) {
                    activeChunkRanges[utteranceId]?.let { it to activeSegmentCallback }
                } ?: return
                val (segment, callback) = callbackAndRange
                if (callback != null) mainHandler.post {
                    if (!released) callback(segment.start, segment.endExclusive)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                val completed = synchronized(this@BozerVoiceOutput) {
                    activeChunkIds.remove(utteranceId)
                    activeChunkRanges.remove(utteranceId)
                    if (activeChunkIds.isEmpty()) {
                        activeUtteranceId = null
                        activeSegmentCallback = null
                        activeCompletedCallback.also { activeCompletedCallback = null }
                    } else null
                }
                completed?.let { callback -> mainHandler.post { if (!released) callback() } }
            }

            override fun onError(utteranceId: String?) {
                val completed = synchronized(this@BozerVoiceOutput) {
                    if (utteranceId != null) {
                        activeChunkIds.remove(utteranceId)
                        activeChunkRanges.remove(utteranceId)
                    }
                    if (activeChunkIds.isEmpty()) {
                        activeUtteranceId = null
                        activeSegmentCallback = null
                        activeCompletedCallback.also { activeCompletedCallback = null }
                    } else null
                }
                publishStatus("La synthèse vocale Android a rencontré une erreur.")
                completed?.let { callback -> mainHandler.post { if (!released) callback() } }
            }
        })

        ready = true
        val voiceName = selectedVoice?.name?.takeIf { it.isNotBlank() }
        val networkLabel = if (selectedVoice?.isNetworkConnectionRequired == true) {
            "connexion Internet possiblement nécessaire"
        } else "hors ligne"
        val genderLabel = when {
            masculineMetadata -> "profil masculin indiqué par Android"
            selectedVoice == null -> "timbre déterminé par la voix Android par défaut"
            else -> "genre non indiqué par Android"
        }
        val voiceLabel = voiceName?.let { " ($it)" }.orEmpty()
        publishStatus(
            "Voix française Android" + voiceLabel + " — $networkLabel ; $genderLabel. Ce n'est pas encore la voix originale BOZER."
        )

        val pending = pendingText
        val pendingId = pendingUtteranceId
        val pendingProgress = pendingSegmentCallback
        val pendingDone = pendingCompletedCallback
        pendingText = null
        pendingUtteranceId = null
        pendingSegmentCallback = null
        pendingCompletedCallback = null
        if (!pending.isNullOrBlank()) {
            speak(pending, pendingId ?: "bozer_reply", pendingProgress ?: { _, _ -> }, pendingDone ?: {})
        }
    }

    @Synchronized
    override fun speak(
        text: String,
        utteranceId: String,
        onSegmentStarted: (Int, Int) -> Unit,
        onCompleted: () -> Unit
    ) {
        if (released) return
        val clean = text
        if (clean.isBlank()) {
            mainHandler.post { onCompleted() }
            return
        }
        if (!ready) {
            pendingText = clean
            pendingUtteranceId = utteranceId
            pendingSegmentCallback = onSegmentStarted
            pendingCompletedCallback = onCompleted
            return
        }

        generation += 1
        engine.stop()
        activeChunkIds.clear()
        activeChunkRanges.clear()
        activeUtteranceId = utteranceId
        activeSegmentCallback = onSegmentStarted
        activeCompletedCallback = onCompleted

        val segments = BozerSpeechText.segments(clean)
        if (segments.isEmpty()) {
            activeUtteranceId = null
            activeSegmentCallback = null
            activeCompletedCallback = null
            mainHandler.post { if (!released) onCompleted() }
            return
        }

        segments.forEachIndexed { index, segment ->
            val chunkId = "$utteranceId#$generation:$index"
            activeChunkIds.add(chunkId)
            activeChunkRanges[chunkId] = segment
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(segment.text, queueMode, null, chunkId)
            if (result == TextToSpeech.ERROR) {
                engine.stop()
                activeChunkIds.clear()
                activeChunkRanges.clear()
                activeUtteranceId = null
                activeSegmentCallback = null
                activeCompletedCallback = null
                publishStatus("Android a refusé une partie du texte à prononcer.")
                mainHandler.post { if (!released) onCompleted() }
                return
            }
        }
    }

    @Synchronized
    override fun stop(utteranceId: String?) {
        val pendingMatches = utteranceId == null || pendingUtteranceId == utteranceId
        if (pendingMatches) {
            pendingText = null
            pendingUtteranceId = null
            pendingSegmentCallback = null
            pendingCompletedCallback = null
        }
        val activeMatches = utteranceId == null || activeUtteranceId == utteranceId
        if (ready && activeMatches) {
            generation += 1
            engine.stop()
            activeChunkIds.clear()
            activeChunkRanges.clear()
            activeUtteranceId = null
            activeSegmentCallback = null
            activeCompletedCallback = null
        }
    }

    override fun isReady(): Boolean = ready && !released
    fun status(): String = currentStatus

    @Synchronized
    override fun release() {
        if (released) return
        pendingText = null
        pendingUtteranceId = null
        pendingSegmentCallback = null
        pendingCompletedCallback = null
        activeUtteranceId = null
        activeChunkIds.clear()
        activeChunkRanges.clear()
        activeSegmentCallback = null
        activeCompletedCallback = null
        ready = false
        released = true
        engine.stop()
        engine.shutdown()
    }

    @Deprecated("Use release()")
    fun shutdown() = release()
}
