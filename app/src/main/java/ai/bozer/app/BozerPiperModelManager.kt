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
import java.security.MessageDigest

/**
 * Downloads the French Piper-derived voice once into app-private storage.
 * Both asset URLs are pinned to a verified Hugging Face repository revision.
 */
class BozerPiperModelManager(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "bozer-voice/fr_FR-mls-medium")
    private val modelFile get() = File(directory, MODEL_FILE_NAME)
    private val tokensFile get() = File(directory, TOKENS_FILE_NAME)

    fun modelDirectory(): File = directory

    fun isInstalled(): Boolean =
        modelFile.isFile && modelFile.length() == MODEL_BYTES &&
            tokensFile.isFile && tokensFile.length() == TOKENS_BYTES

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        isValidAsset(modelFile, MODEL_BYTES, MODEL_SHA256) &&
            isValidAsset(tokensFile, TOKENS_BYTES, null)
    }

    suspend fun install(onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val total = MODEL_BYTES + TOKENS_BYTES
            downloadIfNeeded(
                MODEL_URL, modelFile, MODEL_BYTES, MODEL_SHA256, 0L, total, onProgress
            )
            downloadIfNeeded(
                TOKENS_URL, tokensFile, TOKENS_BYTES, null, MODEL_BYTES, total, onProgress
            )
            check(isInstalled()) { "Les fichiers de la voix sont incomplets." }
            onProgress(total, total)
            directory
        }

    private suspend fun downloadIfNeeded(
        address: String,
        destination: File,
        expectedBytes: Long,
        expectedSha256: String?,
        completedBefore: Long,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        if (isValidAsset(destination, expectedBytes, expectedSha256)) {
            onProgress(completedBefore + expectedBytes, totalBytes)
            return
        }
        if (destination.exists()) {
            check(destination.delete()) {
                "Impossible de remplacer le fichier vocal endommagé."
            }
        }

        val partial = File(destination.parentFile, destination.name + ".part")
        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        if (offset > expectedBytes) {
            partial.delete()
            offset = 0L
        }
        onProgress(completedBefore + offset, totalBytes)

        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BOZER-Android/0.3")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }

        try {
            val status = connection.responseCode
            if (status == 416) {
                partial.delete()
                throw IllegalStateException("La reprise du téléchargement vocal a été refusée. Relance le téléchargement.")
            }
            check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                "Téléchargement vocal refusé (HTTP $status)."
            }

            val append = offset > 0L && status == HttpURLConnection.HTTP_PARTIAL
            if (append) {
                val match = CONTENT_RANGE_REGEX.matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                    ?: throw IllegalStateException("Le serveur a renvoyé une plage vocale invalide.")
                val rangeStart = match.groupValues[1].toLong()
                val rangeTotal = match.groupValues[3].toLongOrNull()
                check(rangeStart == offset && rangeTotal == expectedBytes) {
                    "La reprise ne correspond pas au fichier vocal attendu."
                }
            } else {
                offset = 0L
            }

            val contentLength = connection.contentLengthLong
            if (contentLength >= 0L) {
                check(offset + contentLength == expectedBytes) {
                    "La taille annoncée du fichier vocal est incorrecte."
                }
            }

            val activeContext = currentCoroutineContext()
            var downloaded = offset
            var lastReported = offset
            FileOutputStream(partial, append).use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        activeContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        check(downloaded <= expectedBytes) {
                            "Le fichier vocal reçu dépasse la taille attendue."
                        }
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded == expectedBytes) {
                            onProgress(completedBefore + downloaded, totalBytes)
                            lastReported = downloaded
                        }
                    }
                }
                output.fd.sync()
            }

            check(partial.length() == expectedBytes) {
                "Téléchargement vocal incomplet : ${partial.length()} / $expectedBytes octets. Tu peux reprendre."
            }
            if (expectedSha256 != null && sha256(partial) != expectedSha256) {
                partial.delete()
                throw IllegalStateException("La vérification SHA-256 du modèle vocal a échoué.")
            }
            if (destination.exists()) check(destination.delete()) {
                "Impossible de remplacer le fichier vocal précédent."
            }
            check(partial.renameTo(destination)) {
                "Impossible d'enregistrer le fichier vocal."
            }
            onProgress(completedBefore + expectedBytes, totalBytes)
        } finally {
            connection.disconnect()
            // Keep a valid partial file after interruptions so the next attempt
            // can resume it using HTTP Range.
        }
    }

    private fun isValidAsset(file: File, expectedBytes: Long, expectedSha256: String?): Boolean {
        if (!file.isFile || file.length() != expectedBytes) return false
        return expectedSha256 == null || sha256(file) == expectedSha256
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MODEL_FILE_NAME = "fr_FR-mls-medium.onnx"
        const val TOKENS_FILE_NAME = "tokens.txt"
        const val MODEL_BYTES = 76_733_935L
        const val TOKENS_BYTES = 954L
        private const val MODEL_SHA256 = "d869ae962adb97da4d044e263df38d0b012e10bba6d0c3c8a3cb61190d657d9b"
        private const val REPOSITORY_REVISION = "6df1ed4dba0a0c6d51a92e447ee29fb8474f454f"
        private const val MODEL_URL =
            "https://huggingface.co/XODalG/piper-voices-sherpa/resolve/$REPOSITORY_REVISION/fr/fr_FR/mls/medium/fr_FR-mls-medium.onnx"
        private const val TOKENS_URL =
            "https://huggingface.co/XODalG/piper-voices-sherpa/resolve/$REPOSITORY_REVISION/fr/fr_FR/mls/medium/fr_FR-mls-medium.tokens.txt"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 256 * 1024
        private const val PROGRESS_STEP_BYTES = 1024L * 1024L
        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    }
}
