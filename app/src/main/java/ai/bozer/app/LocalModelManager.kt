package ai.bozer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Downloads the pinned Qwen GGUF into BOZER's private internal storage. */
class LocalModelManager(context: Context) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val partialFile = File(modelDir, "$MODEL_FILENAME.part")
    private val verifiedMarker = File(modelDir, "$MODEL_FILENAME.sha256")

    fun installedModel(): File? = modelFile.takeIf { it.isFile && it.length() == MODEL_BYTES }

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.isFile || modelFile.length() != MODEL_BYTES) return@withContext false
        // Checksum is verified after download; this marker avoids hashing 491 MB on every launch.
        if (verifiedMarker.isFile && verifiedMarker.readText() == MODEL_SHA256) return@withContext true
        val valid = sha256(modelFile) == MODEL_SHA256
        if (valid) verifiedMarker.writeText(MODEL_SHA256) else verifiedMarker.delete()
        valid
    }

    suspend fun download(onProgress: suspend (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            if (verifyInstalled()) return@withContext modelFile
            val requiredBytes = MODEL_BYTES + MINIMUM_FREE_SPACE_BYTES
            check(modelDir.usableSpace >= requiredBytes) {
                "Espace insuffisant : BOZER a besoin d'environ 600 Mo libres pour installer son cerveau."
            }
            partialFile.delete()

            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BOZER-Android")
            }
            try {
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "Téléchargement impossible (HTTP ${connection.responseCode})."
                }
                val announced = connection.contentLengthLong
                if (announced > 0L) check(announced == MODEL_BYTES) {
                    "Taille inattendue du modèle : $announced octets."
                }

                var received = 0L
                connection.inputStream.use { input ->
                    partialFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            onProgress(received, MODEL_BYTES)
                            check(received <= MODEL_BYTES) { "Le fichier téléchargé est trop volumineux." }
                        }
                        output.flush()
                    }
                }
                check(received == MODEL_BYTES) {
                    "Téléchargement incomplet : $received / $MODEL_BYTES octets."
                }
                check(sha256(partialFile) == MODEL_SHA256) {
                    "La vérification SHA-256 du modèle a échoué."
                }
                if (modelFile.exists()) modelFile.delete()
                check(partialFile.renameTo(modelFile)) {
                    "Impossible de finaliser le stockage privé du modèle."
                }
                verifiedMarker.writeText(MODEL_SHA256)
                modelFile
            } catch (error: Exception) {
                partialFile.delete()
                throw error
            } finally {
                connection.disconnect()
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
        const val MODEL_BYTES = 491_400_032L
        const val MINIMUM_FREE_SPACE_BYTES = 128L * 1024L * 1024L
        const val MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/12145bd1d629190a4d44254073650877954d02c9/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
