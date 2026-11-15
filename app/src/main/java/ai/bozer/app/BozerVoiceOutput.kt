package ai.bozer.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Provisional Android system-TTS adapter.
 *
 * This uses speech assets installed by Android, not a trained BOZER voice.
 * It prefers an offline French voice and, when Android exposes that metadata,
 * a masculine-labelled option. The status never infers gender from pitch.
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
    private var activeUtteranceId: String? = null
    private var activeChunkIds = mutableSetOf<String>()
    private var generation = 0L

    private fun publishStatus(message: String) {
        currentStatus = message
        mainHandler.post {
            if (!released) onStatusChanged(message)
        }
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
        // A restrained lower pitch is only a timbre preference, never proof of gender.
        engine.setPitch(if (masculineMetadata) 1.0f else 0.93f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                synchronized(this@BozerVoiceOutput) {
                    activeChunkIds.remove(utteranceId)
                    if (activeChunkIds.isEmpty()) activeUtteranceId = null
                }
            }

            override fun onError(utteranceId: String?) {
                synchronized(this@BozerVoiceOutput) {
                    if (utteranceId != null) activeChunkIds.remove(utteranceId)
                    if (activeChunkIds.isEmpty()) activeUtteranceId = null
                    publishStatus("La synthèse vocale Android a rencontré une erreur.")
                }
            }
        })

        ready = true
        val voiceName = selectedVoice?.name?.takeIf { it.isNotBlank() }
        val networkLabel = if (selectedVoice?.isNetworkConnectionRequired == true) {
            "connexion Internet possiblement nécessaire"
        } else {
            "hors ligne"
        }
        val genderLabel = when {
            masculineMetadata -> "profil masculin indiqué par Android"
            selectedVoice == null -> "timbre et genre déterminés par la voix Android par défaut"
            else -> "genre non indiqué par Android"
        }
        val nameLabel = voiceName?.let { " ($it)" }.orEmpty()
        publishStatus("Voix française Android$nameLabel — $networkLabel ; $genderLabel. Ce n'est pas encore la voix originale BOZER.")

        val pending = pendingText
        val pendingId = pendingUtteranceId
        pendingText = null
        pendingUtteranceId = null
        if (!pending.isNullOrBlank()) speak(pending, pendingId ?: "bozer_reply")
    }

    @Synchronized
    override fun speak(text: String, utteranceId: String) {
        if (released) return
        val clean = text.trim()
        if (clean.isBlank()) return

        if (!ready) {
            pendingText = clean
            pendingUtteranceId = utteranceId
            return
        }

        generation += 1
        engine.stop()
        activeChunkIds.clear()
        activeUtteranceId = utteranceId

        val chunks = BozerSpeechText.chunks(clean)
        if (chunks.isEmpty()) {
            activeUtteranceId = null
            return
        }

        chunks.forEachIndexed { index, chunk ->
            val chunkId = utteranceId + "#" + generation + ":" + index
            activeChunkIds.add(chunkId)
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(chunk, queueMode, null, chunkId)
            if (result == TextToSpeech.ERROR) {
                engine.stop()
                activeChunkIds.clear()
                activeUtteranceId = null
                publishStatus("Android a refusé une partie du texte à prononcer.")
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
        }
        val activeMatches = utteranceId == null || activeUtteranceId == utteranceId
        if (ready && activeMatches) {
            generation += 1
            engine.stop()
            activeChunkIds.clear()
            activeUtteranceId = null
        }
    }

    override fun isReady(): Boolean = ready && !released

    fun status(): String = currentStatus

    @Synchronized
    override fun release() {
        if (released) return
        pendingText = null
        pendingUtteranceId = null
        activeUtteranceId = null
        activeChunkIds.clear()
        ready = false
        released = true
        engine.stop()
        engine.shutdown()
    }

    /** Kept temporarily for compatibility with older callers. */
    @Deprecated("Use release()")
    fun shutdown() = release()
}
