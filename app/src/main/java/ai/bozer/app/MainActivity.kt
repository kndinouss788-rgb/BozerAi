package ai.bozer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID

/**
 * Android host for the actual BOZER Compose experience.
 * Conversation UI and interaction state live in BozerExperience.kt.
 */
class MainActivity : ComponentActivity() {
    private lateinit var engine: LocalLlamaEngine
    private lateinit var voiceOutput: BozerVoiceOutput
    private var voiceResultHandler: ((String) -> Unit)? = null
    private var floatingBubbleResultHandler: ((Boolean) -> Unit)? = null

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(this)
        val active = granted && startFloatingBubbleService()
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("floating_bubble_enabled", active)
            .apply()
        floatingBubbleResultHandler?.invoke(active)
        floatingBubbleResultHandler = null
        if (!granted) {
            Toast.makeText(
                this,
                "Autorise l'affichage par-dessus les autres applications pour activer la bulle.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) voiceResultHandler?.invoke(spokenText)
        }
        voiceResultHandler = null
    }

    private val speechPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognition()
        } else {
            voiceResultHandler = null
            Toast.makeText(this, "Autorise le micro pour parler à BOZER.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LocalLlamaEngine()
        voiceOutput = BozerVoiceOutput(applicationContext)

        val remoteBrain = RemoteBrainClient(
            BuildConfig.BOZER_GATEWAY_URL,
            installationClientId()
        )
        val repository = ChatRepository(
            ChatDatabase(applicationContext),
            engine,
            remoteBrain = remoteBrain
        ).apply {
            internetAvailable = { hasValidatedInternet() }
        }
        val modelManager = LocalModelManager(applicationContext)

        if (
            getPreferences(MODE_PRIVATE).getBoolean("floating_bubble_enabled", false) &&
            Settings.canDrawOverlays(this)
        ) {
            startFloatingBubbleService()
        }

        setContent {
            BozerChatExperience(
                this@MainActivity,
                repository,
                modelManager,
                engine,
                voiceOutput
            )
        }
    }

    private fun installationClientId(): String {
        val preferences = getPreferences(MODE_PRIVATE)
        val existing = preferences.getString("bozer_client_id", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString("bozer_client_id", generated).apply()
        return generated
    }

    fun hasFloatingBubblePermission(): Boolean = Settings.canDrawOverlays(this)

    fun setFloatingBubbleEnabled(enabled: Boolean, onResult: (Boolean) -> Unit) {
        val preferences = getPreferences(MODE_PRIVATE)
        if (!enabled) {
            stopService(Intent(this, BozerFloatingService::class.java))
            preferences.edit().putBoolean("floating_bubble_enabled", false).apply()
            onResult(false)
            return
        }

        if (Settings.canDrawOverlays(this)) {
            val active = startFloatingBubbleService()
            preferences.edit().putBoolean("floating_bubble_enabled", active).apply()
            onResult(active)
            return
        }

        floatingBubbleResultHandler = onResult
        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlaySettingsLauncher.launch(permissionIntent)
    }

    private fun startFloatingBubbleService(): Boolean = try {
        val serviceIntent = Intent(this, BozerFloatingService::class.java)
            .setAction(BozerFloatingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        true
    } catch (error: Exception) {
        Toast.makeText(
            this,
            "Impossible d'activer la bulle BOZER : " + (error.message ?: "erreur Android"),
            Toast.LENGTH_LONG
        ).show()
        false
    }

    fun startVoiceInput(onResult: (String) -> Unit) {
        voiceResultHandler = onResult
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition()
        } else {
            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    internal fun createSpeechRecognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BOZER")
            // Ask the installed Android recognizer for offline speech where available.
            // The provider may ignore this preference; BOZER does not keep the microphone open.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun launchSpeechRecognition() {
        try {
            speechRecognitionLauncher.launch(createSpeechRecognitionIntent())
        } catch (_: Exception) {
            voiceResultHandler = null
            Toast.makeText(
                this,
                "La reconnaissance vocale n'est pas disponible sur ce téléphone.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onDestroy() {
        if (::voiceOutput.isInitialized) voiceOutput.shutdown()
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}
