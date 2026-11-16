package ai.bozer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoicePathInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test
    fun microphoneButtonRequestsRuntimePermissionBeforeOpeningRecognizer() {
        val packageName = context.packageName
        val revoke = device.executeShellCommand(
            "pm revoke $packageName ${Manifest.permission.RECORD_AUDIO}"
        )
        Log.i("BOZER_VOICE", "permission_revoke_result=$revoke")
        assertTrue(
            "Le test doit commencer sans autorisation micro.",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED
        )

        composeRule.onNodeWithTag("bozer_mic").performClick()

        val allow = device.wait(
            Until.findObject(By.text("While using the app")),
            8_000
        ) ?: device.wait(
            Until.findObject(By.text("Only this time")),
            2_000
        ) ?: device.wait(
            Until.findObject(By.text("Allow")),
            2_000
        )
        assertNotNull("Android n'a pas affiché la demande d'autorisation du microphone.", allow)
        allow!!.click()

        composeRule.waitUntil(timeoutMillis = 8_000) {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        assertTrue(
            "L'autorisation micro n'a pas été accordée après confirmation Android.",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        }
        val resolved = context.packageManager.resolveActivity(
            recognizerIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        Log.i(
            "BOZER_VOICE",
            "microphone_permission_granted=true; recognizer_available=${resolved != null}"
        )
        if (resolved != null) {
            device.wait(Until.hasObject(By.pkg(resolved.activityInfo.packageName)), 5_000)
            device.pressBack()
        }

        verifyTextToSpeechLifecycle()
    }

    private fun verifyTextToSpeechLifecycle() {
        val output = BozerVoiceOutput(context)
        val deadline = SystemClock.elapsedRealtime() + 12_000
        while (!output.isReady() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }

        val ready = output.isReady()
        if (ready) {
            assertTrue("Android TTS a refusé la lecture d'un court texte français.", output.speak("Bonjour, je suis BOZER."))
        } else {
            Log.w("BOZER_VOICE", "tts_ready=false; aucun moteur vocal français exploitable sur cet émulateur.")
        }

        output.shutdown()
        output.shutdown()
        assertTrue("Le moteur TTS doit être arrêté après shutdown.", !output.isReady())
        Log.i("BOZER_VOICE", "tts_ready=$ready; shutdown_verified=true")
    }
}
