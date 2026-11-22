package ai.bozer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BozerBundledVoiceTest {
    @Test
    fun frenchVoiceIsBundledAndLoadsWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetPath = "bozer-voice/fr_FR-mls-medium"
        val bundledFiles = context.assets.list(assetPath).orEmpty()

        assertTrue(
            "Le modèle vocal français n'est pas inclus dans l'APK.",
            bundledFiles.contains("fr_FR-mls-medium.onnx")
        )
        assertTrue(
            "Le vocabulaire du modèle vocal n'est pas inclus dans l'APK.",
            bundledFiles.contains("tokens.txt")
        )

        val manager = BozerPiperModelManager(context)
        val directory = manager.installBundledAssets()
        assertTrue("Les fichiers vocaux intégrés n'ont pas passé leur vérification.", manager.verifyInstalled())
        assertNotNull(directory.listFiles()?.firstOrNull { it.name == "fr_FR-mls-medium.onnx" })

        val voice = BozerPiperVoiceOutput()
        try {
            withContext(Dispatchers.IO) { voice.initialize(directory) }
            assertTrue("Le moteur Sherpa-ONNX n'a pas chargé la voix française intégrée.", voice.isReady())
        } finally {
            voice.release()
        }
    }
}
