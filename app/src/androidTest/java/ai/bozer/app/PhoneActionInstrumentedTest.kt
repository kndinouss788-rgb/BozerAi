package ai.bozer.app

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneActionInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test
    fun platformReadinessProbeIsReadOnlyAndDoesNotPretendAssistantServiceExists() {
        val permissionBefore = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val readiness = BozerPlatformReadinessProbe.inspect(context)
        val permissionAfter = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)

        assertEquals(permissionBefore, permissionAfter)
        assertEquals(
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE),
            readiness.hasMicrophoneHardware
        )
        // The current APK intentionally has no VoiceInteractionService declaration.
        assertFalse(readiness.voiceInteractionServiceDeclared)
        assertFalse(readiness.canOfferAssistantSetup)
    }

    @Test
    fun plannerParsesSupportedCommandsAndRejectsInvalidAlarmTimes() {
        assertEquals(
            PhoneAction.SetAlarm(7, 30),
            PhoneActionPlanner.parse("Mets un réveil à 7h30")
        )
        assertEquals(
            PhoneAction.OpenApp("Paramètres"),
            PhoneActionPlanner.parse("Ouvre Paramètres")
        )
        assertEquals(
            PhoneAction.DialNumber("+22670123456"),
            PhoneActionPlanner.parse("Appelle +226 70 12 34 56")
        )
        assertEquals(
            PhoneAction.ComposeSms("+22670123456", "Je serai en retard"),
            PhoneActionPlanner.parse("Envoie un SMS à +226 70 12 34 56 : Je serai en retard")
        )
        assertNull(PhoneActionPlanner.parse("Mets un réveil à 27h90"))
    }

    @Test
    fun sensitivePhoneActionsOnlyCreateDialerAndSmsComposerIntents() {
        val dial = PhoneActionPlanner.createIntent(
            context,
            PhoneAction.DialNumber("+22670123456")
        )
        assertNotNull(dial)
        assertEquals(Intent.ACTION_DIAL, dial!!.action)
        assertEquals("tel", dial.data?.scheme)

        val sms = PhoneActionPlanner.createIntent(
            context,
            PhoneAction.ComposeSms("+22670123456", "Bonjour")
        )
        assertNotNull(sms)
        assertEquals(Intent.ACTION_SENDTO, sms!!.action)
        assertEquals("smsto", sms.data?.scheme)
        assertEquals("Bonjour", sms.getStringExtra("sms_body"))
        assertFalse("BOZER must never send SMS directly", sms.action == "android.intent.action.SEND")
    }

    @Test
    fun confirmedOpenAppActionLaunchesAndroidSettings() {
        val intent = PhoneActionPlanner.createIntent(
            context,
            PhoneAction.OpenApp("Settings")
        )
        assertNotNull("The launcher query did not find Android Settings", intent)
        assertEquals("com.android.settings", intent!!.component?.packageName)
        assertNotNull(intent.resolveActivity(context.packageManager))

        val targetPackage = requireNotNull(
            intent.component?.packageName
                ?: intent.resolveActivity(context.packageManager)?.packageName
        ) { "The Android Settings package could not be resolved" }
        assertEquals("com.android.settings", targetPackage)

        val result = PhoneActionPlanner.execute(context, PhoneAction.OpenApp("Settings"))
        assertTrue(result.message, result.launched)

        // Check Android's activity stack instead of accessibility: the emulator's
        // Settings UI can display a System UI ANR dialog while the activity is running.
        var activityDump = ""
        for (attempt in 0 until 20) {
            activityDump = device.executeShellCommand("dumpsys activity activities")
            if (activityDump.contains("com.android.settings")) break
            SystemClock.sleep(250)
        }
        assertTrue(
            "Android Settings was not started. Activity stack: " + activityDump.takeLast(1_500),
            activityDump.contains("com.android.settings")
        )
    }

    @Test
    fun confirmedAlarmActionOpensClockWithRequestedTime() {
        val action = PhoneAction.SetAlarm(6, 45)
        val intent = PhoneActionPlanner.createIntent(context, action)
        assertNotNull(intent)
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent!!.action)
        assertEquals(6, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(45, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))

        val resolved = intent.resolveActivity(context.packageManager)
        assertNotNull("No clock application handles ACTION_SET_ALARM", resolved)
        val result = PhoneActionPlanner.execute(context, action)
        assertTrue(result.message, result.launched)
        assertTrue(
            "The clock application did not become the foreground application",
            device.wait(Until.hasObject(By.pkg(resolved!!.packageName)), 10_000)
        )
        device.pressBack()
    }
}
