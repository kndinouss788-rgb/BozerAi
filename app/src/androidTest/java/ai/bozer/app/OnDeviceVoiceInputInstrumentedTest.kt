package ai.bozer.app

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceVoiceInputInstrumentedTest {
    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val statuses = mutableListOf<String>()
    private val voiceInput by lazy {
        OnDeviceVoiceInput(context, onText = {}, onStatus = { statuses.add(it) })
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { voiceInput.close() }
    }

    @Test
    fun voiceInputUsesOnDeviceAvailabilityAndCanBeStopped() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var started = false
        instrumentation.runOnMainSync { started = voiceInput.start() }
        if (started) {
            assertTrue("The on-device recognizer did not enter listening state.", voiceInput.isListening)
            instrumentation.runOnMainSync { voiceInput.cancel() }
            assertFalse("Cancel did not stop the recognizer.", voiceInput.isListening)
            assertTrue(statuses.any { it.contains("annulée", ignoreCase = true) })
        } else {
            assertFalse(voiceInput.isListening)
            assertTrue(
                "Unavailable local recognition must be reported honestly.",
                statuses.any {
                    it.contains("hors ligne", ignoreCase = true) ||
                        it.contains("Android", ignoreCase = true)
                }
            )
        }
    }
}
