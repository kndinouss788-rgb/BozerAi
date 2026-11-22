package ai.bozer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Installs the model that is already embedded in the APK.
 *
 * No model download, account, API key or network connection is required.
 * The packaged asset is copied once to private app storage because llama.cpp
 * needs a normal seekable file for memory-mapped GGUF loading.
 */
class LocalModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val partialFile = File(modelDir, "$MODEL_FILENAME.part")
    private val verifiedMarker = File(modelDir, "$MODEL_FILENAME.sha256")

    fun installedModel(): File? = modelFile.takeIf {
        it.isFile && it.length() >= MINIMUM_MODEL_BYTES && verifiedMarker.isFile
    }

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        val metadata = readBundledMetadata()
        if (!modelFile.isFile || modelFile.length() != metadata.bytes) return@withContext false
        if (verifiedMarker.isFile && verifiedMarker.readText().trim() == metadata.sha256) {
            return@withContext true
        }
        val valid = sha256(modelFile) == metadata.sha256
        if (valid) verifiedMarker.writeText(metadata.sha256) else verifiedMarker.delete()
        valid
    }

    suspend fun installBundled(
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (verifyInstalled()) return@withContext modelFile

        val metadata = readBundledMetadata()
        val requiredBytes = metadata.bytes + MINIMUM_FREE_SPACE_BYTES
        check(modelDir.usableSpace >= requiredBytes) {
            "Espace insuffisant : libère environ ${(requiredBytes / (1024L * 1024L))} Mo pour préparer le cerveau intégré à BOZER."
        }

        partialFile.delete()
        try {
            var copied = 0L
            appContext.assets.open(MODEL_ASSET).buffered().use { input ->
                partialFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(copied, metadata.bytes)
                        check(copied <= metadata.bytes) {
                            "Le cerveau intégré dépasse la taille annoncée."
                        }
                    }
                    output.flush()
                }
            }

            check(copied == metadata.bytes) {
                "Copie incomplète du cerveau intégré : $copied / ${metadata.bytes} octets."
            }
            check(sha256(partialFile) == metadata.sha256) {
                "La vérification du cerveau intégré a échoué. Le fichier n'a pas été installé."
            }

            if (modelFile.exists()) modelFile.delete()
            check(partialFile.renameTo(modelFile)) {
                "Impossible de finaliser l'installation privée du cerveau."
            }
            verifiedMarker.writeText(metadata.sha256)
            modelFile
        } catch (error: Exception) {
            partialFile.delete()
            throw error
        }
    }

    private fun readBundledMetadata(): ModelMetadata {
        val line = appContext.assets.open(MODEL_METADATA_ASSET)
            .bufferedReader(Charsets.UTF_8).use { it.readLine().orEmpty().trim() }
        val fields = line.split(Regex("\\s+"))
        val hash = fields.getOrNull(0).orEmpty().lowercase()
        val bytes = fields.getOrNull(1)?.toLongOrNull() ?: 0L
        require(hash.matches(Regex("[a-f0-9]{64}")) && bytes >= MINIMUM_MODEL_BYTES) {
            "Les informations du cerveau intégré sont absentes ou invalides."
        }
        return ModelMetadata(hash, bytes)
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

    private data class ModelMetadata(val sha256: String, val bytes: Long)

    companion object {
        const val MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_ASSET = "bozer-model.gguf"
        const val MODEL_METADATA_ASSET = "bozer-model.sha256"
        const val MINIMUM_MODEL_BYTES = 900_000_000L
        const val MINIMUM_FREE_SPACE_BYTES = 160L * 1024L * 1024L
    }
}
