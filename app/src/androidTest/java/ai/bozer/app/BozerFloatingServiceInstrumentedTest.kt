package ai.bozer.app

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BozerFloatingServiceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @After
    fun stopService() {
        context.stopService(BozerFloatingService.stopIntent(context))
        device.executeShellCommand("appops set ai.bozer.app SYSTEM_ALERT_WINDOW ignore")
    }

    @Test
    fun overlayDoesNotStartWithoutExplicitSystemPermission() {
        device.executeShellCommand("appops set ai.bozer.app SYSTEM_ALERT_WINDOW ignore")
        assertFalse(Settings.canDrawOverlays(context))

        context.startForegroundService(Intent(context, BozerFloatingService::class.java))
        assertFalse(
            "The service must not display an overlay when Android permission is denied.",
            device.wait(Until.hasObject(By.desc("Ouvrir BOZER")), 2_000)
        )
    }

    @Test
    fun permittedBubbleAppearsOpensBozerAndCanBeStopped() {
        device.executeShellCommand("appops set ai.bozer.app SYSTEM_ALERT_WINDOW allow")
        assertTrue("The test environment did not grant overlay permission.", Settings.canDrawOverlays(context))

        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        assertTrue(device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 10_000))

        context.startForegroundService(Intent(context, BozerFloatingService::class.java))
        val bubble = device.wait(Until.findObject(By.desc("Ouvrir BOZER")), 10_000)
        assertTrue("The real Android overlay window was not attached.", bubble != null)
        assertTrue("The service did not enter its active state.", BozerFloatingService.isRunning)

        bubble!!.click()
        assertTrue(
            "Tapping the floating bubble did not open BOZER.",
            device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 10_000)
        )

        context.stopService(BozerFloatingService.stopIntent(context))
        assertTrue(
            "The overlay remained visible after the service was stopped.",
            device.wait(Until.gone(By.desc("Ouvrir BOZER")), 10_000)
        )
        assertFalse(BozerFloatingService.isRunning)
    }
}
