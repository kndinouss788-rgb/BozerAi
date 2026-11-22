package ai.bozer.app

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
    fun plannerParsesSupportedCommandsAndRejectsInvalidAlarmTimes() {
        val standardAlarm = PhoneActionPlanner.parse("Mets un réveil à 7h30") as? PhoneAction.SetAlarm
        assertNotNull("La commande de réveil simple n'a pas été comprise.", standardAlarm)
        assertEquals(7, standardAlarm!!.hour)
        assertEquals(30, standardAlarm.minute)
        assertTrue("Le réveil doit viser une heure future.", standardAlarm.triggerAtMillis > System.currentTimeMillis())

        val tomorrowAlarm = PhoneActionPlanner.parse("Réveille-moi demain à 6 h 30") as? PhoneAction.SetAlarm
        assertNotNull("La date relative « demain » n'a pas été comprise.", tomorrowAlarm)
        assertEquals(6, tomorrowAlarm!!.hour)
        assertEquals(30, tomorrowAlarm.minute)
        val tomorrow = java.time.Instant.ofEpochMilli(tomorrowAlarm.triggerAtMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        assertEquals(java.time.LocalDate.now().plusDays(1), tomorrow)
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
    fun actionJournalLabelsDoNotIncludePhoneNumbersOrSmsBodies() {
        val sms = PhoneAction.ComposeSms("+22670123456", "Texte privé du message")
        assertEquals("PRÉPARER_SMS", PhoneActionExecutor.actionType(sms))
        assertEquals("Préparer un brouillon SMS", PhoneActionExecutor.actionSummary(sms))
        assertFalse(PhoneActionExecutor.actionSummary(sms).contains("+226"))
        assertFalse(PhoneActionExecutor.actionSummary(sms).contains("Texte privé"))

        val alarm = PhoneAction.SetAlarm(6, 30)
        val handedOff = ActionExecutionResult(
            launched = true,
            message = "Réveil programmé par Android."
        )
        assertEquals("PROGRAMMÉ", PhoneActionExecutor.resultStatus(alarm, handedOff))
    }

    @Test
    fun phoneActionsCannotLaunchWithoutExplicitConfirmation() {
        val actions = listOf(
            PhoneAction.OpenApp("Settings"),
            PhoneAction.SetAlarm(6, 45),
            PhoneAction.DialNumber("+22670123456"),
            PhoneAction.ComposeSms("+22670123456", "Bonjour")
        )
        actions.forEach { action ->
            val result = PhoneActionPlanner.execute(context, action)
            assertFalse(
                "Une action a été lancée sans confirmation explicite : $action",
                result.launched
            )
            assertTrue(
                "Le refus doit expliquer que la confirmation est requise",
                result.message.contains("confirmation", ignoreCase = true)
            )
        }
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

        val result = PhoneActionPlanner.execute(context, PhoneAction.OpenApp("Settings"), confirmed = true)
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
    fun confirmedAlarmActionUsesExactAlarmManagerAndPermissionGates() {
        val action = PhoneAction.SetAlarm(6, 45)
        val result = PhoneActionExecutor.execute(context, action, confirmed = true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            assertFalse(result.launched)
            assertTrue(result.retryable)
            assertTrue(result.message.contains("autoriser", ignoreCase = true))
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            assertFalse(result.launched)
            assertTrue(result.retryable)
            assertTrue(result.message.contains("notifications", ignoreCase = true))
            return
        }

        assertTrue(result.message, result.launched)
        assertTrue(result.message, result.message.contains("programmé", ignoreCase = true))
        assertTrue(
            "L'alarme réellement acceptée par Android doit être conservée pour la restauration après redémarrage.",
            BozerAlarmStore.all(context).any { it.triggerAtMillis == action.triggerAtMillis }
        )
    }
}
