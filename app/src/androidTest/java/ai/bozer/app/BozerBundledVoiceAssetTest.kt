package ai.bozer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BozerBundledVoiceAssetTest {
    @Test
    fun frenchVoiceIsPackagedVerifiedAndLoadableWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = BozerPiperModelManager(context)

        val directory = manager.installBundledAssets()
        assertTrue("La voix intégrée n'a pas passé sa vérification SHA-256.", manager.verifyInstalled())

        val voice = BozerPiperVoiceOutput()
        try {
            withContext(Dispatchers.IO) { voice.initialize(directory) }
            assertTrue("Le moteur vocal Sherpa-ONNX n'a pas chargé le modèle français intégré.", voice.isReady())
        } finally {
            voice.release()
        }
    }
}
