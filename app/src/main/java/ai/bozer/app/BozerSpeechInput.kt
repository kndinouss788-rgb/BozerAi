package ai.bozer.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * BOZER speech capture using Android's recognition service without opening
 * a separate voice-assistant activity. Prefers the on-device recognizer.
 */
class BozerSpeechInput(
    context: Context,
    private val onText: (String) -> Unit,
    private val onFailure: (Int) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun start(languageTag: String = "fr-FR"): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return false
        stop()
        val speech = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onError(error: Int) {
                onFailure(error)
                destroyRecognizer()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isNotBlank()) onText(text)
                destroyRecognizer()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag.ifBlank { Locale.getDefault().toLanguageTag() })
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BOZER")
        }
        speech.startListening(intent)
        return true
    }

    fun stop() {
        recognizer?.cancel()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
    }
}
