package ai.bozer.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Explicit, press-to-talk speech input.
 *
 * It deliberately creates only Android's on-device recognizer. It never falls
 * back to a cloud recognizer and never starts listening in the background.
 */
class OnDeviceVoiceInput(
    context: Context,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit
) : RecognitionListener {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    @Volatile
    var isListening: Boolean = false
        private set

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onStatus("La reconnaissance vocale locale demande Android 12 ou plus récent.")
            return false
        }
        if (!isAvailable()) {
            onStatus("La reconnaissance vocale hors ligne n'est pas disponible sur cet appareil.")
            return false
        }

        return try {
            closeRecognizer()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext).also {
                it.setRecognitionListener(this)
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(request)
            isListening = true
            onStatus("Écoute locale en cours…")
            true
        } catch (_: RuntimeException) {
            closeRecognizer()
            onStatus("Android n'a pas pu démarrer la reconnaissance vocale locale.")
            false
        }
    }

    fun cancel() {
        recognizer?.cancel()
        closeRecognizer()
        onStatus("Écoute annulée.")
    }

    fun close() {
        closeRecognizer()
    }

    private fun closeRecognizer() {
        isListening = false
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onStatus("Je t'écoute…")
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        onStatus("Je traite ta voix sur l'appareil…")
    }

    override fun onError(error: Int) {
        closeRecognizer()
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Le microphone a rencontré une erreur."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android n'a pas accordé l'accès au microphone."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Le moteur de reconnaissance locale a signalé une erreur. Aucune recherche Internet n'a été lancée par BOZER."
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai pas reconnu de parole. Réessaie."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "La reconnaissance vocale est déjà occupée."
            else -> "La reconnaissance vocale s'est arrêtée (code $error)."
        }
        onStatus(message)
    }

    override fun onResults(results: Bundle?) {
        val transcript = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        closeRecognizer()
        if (transcript.isBlank()) {
            onStatus("Aucune parole n'a été reconnue.")
        } else {
            onText(transcript)
            onStatus("Voix transcrite sur l'appareil.")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
