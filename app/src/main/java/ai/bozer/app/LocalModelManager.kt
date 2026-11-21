package ai.bozer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Installs the foundation model bundled inside the BOZER APK into app-private storage.
 * No network request and no user-facing model-download action are required.
 */
class LocalModelManager(private val context: Context) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val partialFile = File(modelDir, "$MODEL_FILENAME.part")
    private val verifiedMarker = File(modelDir, "$MODEL_FILENAME.sha256")

    fun installedModel(): File? = modelFile.takeIf { it.isFile && it.length() == MODEL_BYTES }

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.isFile || modelFile.length() != MODEL_BYTES) return@withContext false
        if (verifiedMarker.isFile && verifiedMarker.readText() == MODEL_SHA256) return@withContext true
        val valid = sha256(modelFile) == MODEL_SHA256
        if (valid) verifiedMarker.writeText(MODEL_SHA256) else verifiedMarker.delete()
        valid
    }

    suspend fun installBundledModel(onProgress: suspend (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            if (verifyInstalled()) return@withContext modelFile
            check(modelDir.usableSpace >= MODEL_BYTES + MINIMUM_FREE_SPACE_BYTES) {
                "Espace insuffisant : BOZER a besoin d'environ 600 Mo libres pour préparer son cerveau."
            }
            partialFile.delete()
            try {
                val assetLength = context.assets.openFd(MODEL_ASSET_PATH).use { it.length }
                check(assetLength == MODEL_BYTES) {
                    "Le cerveau intégré à cet APK a une taille inattendue ($assetLength octets)."
                }
                context.assets.open(MODEL_ASSET_PATH).buffered().use { input ->
                    partialFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var copied = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(copied, MODEL_BYTES)
                            check(copied <= MODEL_BYTES) { "Le cerveau intégré dépasse la taille attendue." }
                        }
                        output.flush()
                        check(copied == MODEL_BYTES) {
                            "Copie incomplète du cerveau intégré : $copied / $MODEL_BYTES octets."
                        }
                    }
                }
                check(sha256(partialFile) == MODEL_SHA256) {
                    "La vérification SHA-256 du cerveau intégré a échoué."
                }
                if (modelFile.exists()) modelFile.delete()
                check(partialFile.renameTo(modelFile)) {
                    "Impossible de finaliser le stockage privé du cerveau BOZER."
                }
                verifiedMarker.writeText(MODEL_SHA256)
                modelFile
            } catch (error: Exception) {
                partialFile.delete()
                throw error
            }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        const val MODEL_ASSET_PATH = "bozer/$MODEL_FILENAME"
        const val MODEL_BYTES = 491_400_032L
        const val MINIMUM_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        const val MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    }
}
