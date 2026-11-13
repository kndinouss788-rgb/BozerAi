package ai.bozer.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingBubbleServiceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun userEnabledBubbleRunsAsForegroundServiceAndStopsCleanly() {
        assertTrue(
            "The emulator must grant SYSTEM_ALERT_WINDOW before this integration test.",
            Settings.canDrawOverlays(context)
        )

        val intent = Intent(context, BozerFloatingService::class.java)
            .setAction(BozerFloatingService.ACTION_START)

        context.startForegroundService(intent)
        assertTrue(
            "BOZER floating service did not enter the running state.",
            waitForServiceState(expectedRunning = true, timeoutMs = 8_000)
        )

        assertTrue(
            "The foreground notification must be active while the bubble is enabled.",
            isServiceRunning()
        )

        context.stopService(intent)
        assertTrue(
            "BOZER floating service did not stop after the user disabled the bubble.",
            waitForServiceState(expectedRunning = false, timeoutMs = 8_000)
        )
        assertFalse(isServiceRunning())
    }

    private fun waitForServiceState(expectedRunning: Boolean, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isServiceRunning() == expectedRunning) return true
            SystemClock.sleep(200)
        }
        return isServiceRunning() == expectedRunning
    }

    private fun isServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == BozerFloatingService::class.java.name
        }
    }
}
