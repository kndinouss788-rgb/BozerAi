package ai.bozer.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Local French BOZER speech output. It uses the Sherpa-ONNX runtime and the
 * 125-speaker MLS voice checkpoint. Speaker 55 is the current masculine
 * candidate, pending confirmation by listening to the synthesized output.
 *
 * This is a BOZER-integrated voice profile, not an original speaker clone.
 */
class BozerPiperVoiceOutput(
    private val onStatusChanged: (String) -> Unit = {}
) : BozerSpeechEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0L)
    private val engineLock = Any()

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var released = false
    @Volatile private var currentStatus = "Voix locale BOZER non chargée."

    private var playbackJob: Job? = null

    fun initialize(modelDirectory: File) {
        check(!released) { "Le moteur vocal a été fermé." }
        check(modelDirectory.isDirectory) { "Dossier du modèle vocal introuvable." }
        val model = File(modelDirectory, "fr_FR-mls-medium.onnx")
        val tokens = File(modelDirectory, BozerPiperModelManager.TOKENS_FILE_NAME)
        check(model.isFile && tokens.isFile) { "Fichiers du modèle vocal manquants." }

        synchronized(engineLock) {
            if (tts != null) return
            val candidate = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            tokens = tokens.absolutePath,
                            dataDir = "",
                            noiseScale = 0.55f,
                            noiseScaleW = 0.8f,
                            lengthScale = 1.0f
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1,
                    silenceScale = 0.22f
                )
            )
            val speakerCount = candidate.numSpeakers()
            if (speakerCount != EXPECTED_SPEAKER_COUNT || MALE_SPEAKER_ID !in 0 until speakerCount) {
                candidate.release()
                error(
                    "Modèle inattendu : " + speakerCount +
                        " profils ; le modèle MLS attendu en expose 125."
                )
            }
            tts = candidate
        }
        publishStatus("Voix française locale chargée — profil candidat 55 — hors ligne ; timbre à confirmer par écoute.")
    }

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
        val engine = tts
        if (engine == null) {
            publishStatus("La voix locale n'est pas chargée. La voix Android peut prendre le relais.")
            mainHandler.post { onCompleted() }
            return
        }

        val request = generation.incrementAndGet()
        stopTrack()
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val pieces = BozerSpeechText.segments(clean, maxCharacters = 220)
                for (piece in pieces) {
                    if (generation.get() != request) break
                    val audio = synchronized(engineLock) {
                        if (generation.get() != request || released) null
                        else engine.generate(piece.text, sid = MALE_SPEAKER_ID, speed = SPEECH_SPEED)
                    } ?: break
                    if (audio.samples.isNotEmpty() && generation.get() == request) {
                        mainHandler.post {
                            if (!released && generation.get() == request) {
                                onSegmentStarted(piece.start, piece.endExclusive)
                            }
                        }
                        playAudio(audio.samples, audio.sampleRate, request)
                    }
                }
            } catch (error: Exception) {
                if (generation.get() == request) {
                    publishStatus("Erreur de synthèse locale : " + (error.message ?: "inconnue") + ".")
                }
            } finally {
                if (generation.get() == request) {
                    stopTrack()
                    mainHandler.post { if (!released && generation.get() == request) onCompleted() }
                }
            }
        }
    }

    private fun playAudio(samples: FloatArray, sampleRate: Int, request: Long) {
        if (samples.isEmpty() || sampleRate <= 0 || generation.get() != request) return
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        check(minBuffer > 0) { "Format audio non pris en charge par Android." }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuffer, 16 * 1024))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeTrack = track
        try {
            track.play()
            var offset = 0
            while (offset < samples.size && generation.get() == request) {
                val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "Écriture audio interrompue (" + written + ")." }
                offset += written
            }
        } finally {
            if (activeTrack === track) activeTrack = null
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
            track.release()
        }
    }

    private fun stopTrack() {
        val track = activeTrack ?: return
        activeTrack = null
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            track.flush()
            track.stop()
        }
        runCatching { track.release() }
    }

    override fun stop(utteranceId: String?) {
        generation.incrementAndGet()
        playbackJob?.cancel()
        stopTrack()
    }

    override fun isReady(): Boolean = tts != null && !released

    fun status(): String = currentStatus

    private fun publishStatus(message: String) {
        currentStatus = message
        mainHandler.post {
            if (!released) onStatusChanged(message)
        }
    }

    override fun release() {
        if (released) return
        released = true
        generation.incrementAndGet()
        playbackJob?.cancel()
        stopTrack()
        synchronized(engineLock) {
            tts?.release()
            tts = null
        }
        scope.cancel()
    }

    companion object {
        private const val EXPECTED_SPEAKER_COUNT = 125
        private const val MALE_SPEAKER_ID = 55
        private const val SPEECH_SPEED = 0.97f
    }
}
