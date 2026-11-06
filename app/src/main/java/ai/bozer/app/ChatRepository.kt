package ai.bozer.app

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatRepository(
    private val database: ChatDatabase,
    private val model: LocalLlamaEngine
) {
    private val inferenceLock = Mutex()

    fun history(): List<ChatMessage> = database.history()
    fun save(message: ChatMessage) = database.save(message)

    suspend fun reply(history: List<ChatMessage>): String = inferenceLock.withLock {
        val latestUserText = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (asksForCurrentTime(latestUserText)) {
            return@withLock "Il est " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH 'h' mm", Locale.FRANCE)) + "."
        }

        // Keep several bounded turns so BOZER can follow references and remember recent details.
        val transcript = buildString {
            append("Voici les derniers échanges, dans l'ordre chronologique. ")
            append("Utilise-les pour comprendre le contexte et les références.\n")
            history.takeLast(8).forEach { item ->
                val content = item.content.trim().take(480)
                when (item.role) {
                    "user" -> append("[Utilisateur] ").append(content).append('\n')
                    "assistant" -> append("[BOZER] ").append(content).append('\n')
                }
            }
            append("\nLe dernier message utilisateur est la demande à traiter. ")
            append("Réponds naturellement, directement et avec assez de détails pour être utile. ")
            append("Si une information manque, pose une question au lieu de l'inventer.")
        }

        try {
            model.generate(transcript)
        } catch (error: Exception) {
            "Je rencontre un problème avec mon moteur local : ${error.message ?: "erreur inconnue"}. Réessaie dans un instant."
        }
    }

    private fun asksForCurrentTime(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf("quelle heure", "quel heure", "il est quelle heure", "il fait quelle heure", "il fait quel heure")
            .any(normalized::contains)
    }
}
