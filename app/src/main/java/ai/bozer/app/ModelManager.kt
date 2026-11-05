package ai.bozer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads the official Qwen GGUF into app-private internal storage.
 * A .part file is never treated as an installed model.
 */
class ModelManager(context: Context) {
    private val modelDirectory = File(context.filesDir, "models")
    val modelFile: File get() = File(modelDirectory, MODEL_FILENAME)

    suspend fun ensureDownloaded(onProgress: suspend (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (modelFile.exists() && modelFile.length() == EXPECTED_BYTES &&
                sha256(modelFile) == EXPECTED_SHA256) return@withContext modelFile

            modelDirectory.mkdirs()
            val partial = File(modelDirectory, "$MODEL_FILENAME.part")
            partial.delete()

            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "BOZER-Android")
            }
            try {
                connection.connect()
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "Téléchargement impossible (HTTP ${connection.responseCode})."
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
                FileOutputStream(partial).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, total)
                        }
                        output.fd.sync()
                    }
                }
                check(partial.length() == EXPECTED_BYTES) {
                    "Le fichier téléchargé est incomplet (taille inattendue)."
                }
                check(sha256(partial) == EXPECTED_SHA256) {
                    "La vérification SHA-256 du modèle a échoué."
                }
                check(partial.renameTo(modelFile)) {
                    "Impossible d'installer le modèle dans le stockage privé."
                }
                modelFile
            } catch (error: Exception) {
                partial.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

    fun isInstalled(): Boolean = modelFile.isFile &&
        modelFile.length() == EXPECTED_BYTES

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
        const val EXPECTED_BYTES = 514_000_000L
        const val EXPECTED_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
        const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/12145bd1d629190a4d44254073650877954d02c9/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    }
}
