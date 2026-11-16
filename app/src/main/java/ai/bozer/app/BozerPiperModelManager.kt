package ai.bozer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the French Piper-derived voice once. The ONNX weights stay in
 * app-private storage and are used locally after the first download.
 */
class BozerPiperModelManager(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "bozer-voice/fr_FR-mls-medium")
    private val modelFile get() = File(directory, MODEL_FILE_NAME)
    private val tokensFile get() = File(directory, TOKENS_FILE_NAME)

    fun modelDirectory(): File = directory

    fun isInstalled(): Boolean =
        modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES &&
            tokensFile.isFile && tokensFile.length() >= MIN_TOKENS_BYTES

    suspend fun install(onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            downloadIfNeeded(MODEL_URL, modelFile, MIN_MODEL_BYTES, onProgress)
            downloadIfNeeded(TOKENS_URL, tokensFile, MIN_TOKENS_BYTES, onProgress)
            check(isInstalled()) { "Les fichiers de la voix sont incomplets." }
            directory
        }

    private suspend fun downloadIfNeeded(
        address: String,
        destination: File,
        minimumBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        if (destination.isFile && destination.length() >= minimumBytes) return

        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BOZER-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Téléchargement refusé (HTTP " + connection.responseCode + ")."
            }
            val total = connection.contentLengthLong
            val downloadContext = currentCoroutineContext()
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        downloadContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }
            check(partial.length() >= minimumBytes) {
                "Le fichier reçu est trop petit (" + partial.length() + " octets)."
            }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Impossible d'enregistrer le fichier vocal." }
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MODEL_FILE_NAME = "fr_FR-mls-medium.onnx"
        private const val TOKENS_FILE_NAME = "tokens.txt"
        private const val MIN_MODEL_BYTES = 20L * 1024L * 1024L
        private const val MIN_TOKENS_BYTES = 100L
        private const val MODEL_URL =
            "https://huggingface.co/csukuangfj/vits-piper-fr_FR-mls-medium/resolve/main/fr_FR-mls-medium.onnx"
        private const val TOKENS_URL =
            "https://huggingface.co/csukuangfj/vits-piper-fr_FR-mls-medium/resolve/main/tokens.txt"
    }
}
