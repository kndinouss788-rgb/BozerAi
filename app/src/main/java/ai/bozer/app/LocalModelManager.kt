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
 * Installs BOZER's optional local language model in app-private storage.
 *
 * The large GGUF file is deliberately kept out of the APK. It is downloaded
 * only when the user asks to enable the offline brain, verified by SHA-256,
 * retained across app updates, and resumed after an interrupted transfer.
 */
class LocalModelManager(private val context: Context) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILENAME)
    private val partialFile = File(modelDir, "$MODEL_FILENAME.part")
    private val verifiedMarker = File(modelDir, "$MODEL_FILENAME.sha256")

    fun installedModel(): File? = modelFile.takeIf { it.isFile && it.length() == MODEL_BYTES }

    suspend fun verifiedInstalledModel(): File? =
        if (verifyInstalled()) modelFile else null

    suspend fun verifyInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.isFile || modelFile.length() != MODEL_BYTES) return@withContext false
        val marker = "$MODEL_SHA256\n$MODEL_BYTES\n${modelFile.lastModified()}"
        if (verifiedMarker.isFile && verifiedMarker.readText() == marker) return@withContext true
        val valid = sha256(modelFile) == MODEL_SHA256
        if (valid) verifiedMarker.writeText(marker) else verifiedMarker.delete()
        valid
    }

    /**
     * Downloads the pinned model on demand. Existing partial bytes are resumed
     * with HTTP Range when supported; if the server ignores Range, the transfer
     * safely restarts from byte zero. Incomplete downloads are kept for retry.
     */
    suspend fun installModel(onProgress: suspend (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            if (verifyInstalled()) return@withContext modelFile

            modelDir.mkdirs()
            if (modelFile.exists()) {
                check(modelFile.delete()) {
                    "Le fichier du cerveau local est endommagé et ne peut pas être remplacé."
                }
            }
            verifiedMarker.delete()
            var offset = partialFile.takeIf { it.isFile }?.length() ?: 0L
            if (offset > MODEL_BYTES) {
                partialFile.delete()
                offset = 0L
            }

            // Recover a complete download if the process stopped just before
            // the final rename/marker write.
            if (offset == MODEL_BYTES) {
                check(sha256(partialFile) == MODEL_SHA256) {
                    partialFile.delete()
                    "La vérification SHA-256 du téléchargement précédent a échoué. Relance le téléchargement."
                }
                if (modelFile.exists()) modelFile.delete()
                check(partialFile.renameTo(modelFile)) {
                    "Impossible de finaliser le stockage privé du cerveau BOZER."
                }
                verifiedMarker.writeText("$MODEL_SHA256\n$MODEL_BYTES\n${modelFile.lastModified()}")
                onProgress(MODEL_BYTES, MODEL_BYTES)
                return@withContext modelFile
            }

            val requiredSpace = (MODEL_BYTES - offset) + MINIMUM_FREE_SPACE_BYTES
            check(modelDir.usableSpace >= requiredSpace) {
                "Espace insuffisant : prévois environ 600 Mo libres pour télécharger le cerveau local BOZER."
            }
            onProgress(offset, MODEL_BYTES)

            val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BOZER-Android/0.3")
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }

            try {
                val status = connection.responseCode
                if (status == 416) {
                    partialFile.delete()
                    throw IllegalStateException("La reprise du téléchargement a été refusée. Relance le téléchargement depuis le début.")
                }
                check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                    "Téléchargement du cerveau refusé (HTTP $status). Réessaie avec une connexion Internet stable."
                }

                val append = offset > 0L && status == HttpURLConnection.HTTP_PARTIAL
                if (append) {
                    val range = connection.getHeaderField("Content-Range")
                    val match = CONTENT_RANGE_REGEX.matchEntire(range.orEmpty())
                        ?: throw IllegalStateException("Le serveur a renvoyé une plage de téléchargement invalide.")
                    val rangeStart = match.groupValues[1].toLong()
                    val rangeTotal = match.groupValues[3].toLongOrNull()
                    check(rangeStart == offset && rangeTotal == MODEL_BYTES) {
                        "La reprise du téléchargement ne correspond pas au cerveau attendu."
                    }
                } else {
                    // A server that ignores Range answers 200; overwrite the old partial file.
                    offset = 0L
                }

                val responseLength = connection.contentLengthLong
                if (responseLength >= 0L) {
                    check(offset + responseLength == MODEL_BYTES) {
                        "La taille annoncée par le serveur ne correspond pas au cerveau BOZER."
                    }
                }

                val activeContext = currentCoroutineContext()
                var written = offset
                var lastProgress = offset
                FileOutputStream(partialFile, append).use { output ->
                    connection.inputStream.buffered().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            activeContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            check(written <= MODEL_BYTES) {
                                "Le fichier reçu dépasse la taille attendue ; le téléchargement a été annulé."
                            }
                            if (written - lastProgress >= PROGRESS_STEP_BYTES || written == MODEL_BYTES) {
                                onProgress(written, MODEL_BYTES)
                                lastProgress = written
                            }
                        }
                    }
                    output.fd.sync()
                }

                check(partialFile.length() == MODEL_BYTES) {
                    "Téléchargement incomplet : ${partialFile.length() / MEBIBYTE} / ${MODEL_BYTES / MEBIBYTE} Mo. Appuie sur Reprendre pour continuer sans recommencer."
                }
                if (sha256(partialFile) != MODEL_SHA256) {
                    partialFile.delete()
                    throw IllegalStateException("La vérification SHA-256 du cerveau BOZER a échoué. Le téléchargement doit être recommencé.")
                }

                if (modelFile.exists()) check(modelFile.delete()) {
                    "Impossible de remplacer l'ancien fichier du cerveau BOZER."
                }
                check(partialFile.renameTo(modelFile)) {
                    "Impossible d'enregistrer le cerveau BOZER dans le stockage privé."
                }
                verifiedMarker.writeText("$MODEL_SHA256\n$MODEL_BYTES\n${modelFile.lastModified()}")
                onProgress(MODEL_BYTES, MODEL_BYTES)
                modelFile
            } finally {
                connection.disconnect()
                // Keep a partial download after network errors or cancellation so
                // the next attempt can resume instead of starting from zero.
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
        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/12145bd1d629190a4d44254073650877954d02c9/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 256 * 1024
        private const val PROGRESS_STEP_BYTES = 1024L * 1024L
        private const val MEBIBYTE = 1024L * 1024L
        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    }
}
