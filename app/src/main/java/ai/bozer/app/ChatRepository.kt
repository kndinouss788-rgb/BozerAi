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

        // Keep a short recent context: long transcripts make small on-device models
        // needlessly slow and often reduce answer quality.
        val transcript = buildString {
            history.takeLast(6).forEach { item ->
                when (item.role) {
                    "user" -> append("Utilisateur : ").append(item.content).append('\n')
                    "assistant" -> append("BOZER : ").append(item.content).append('\n')
                }
            }
            append("Réponds simplement au dernier message de l'utilisateur en français.")
        }
        try {
            model.generate(transcript)
        } catch (error: Exception) {
            "Je n'ai pas pu générer cette réponse avec mon moteur local : ${error.message ?: "erreur inconnue"}. Réessaie après avoir vérifié que le modèle est chargé."
        }
    }

    private fun asksForCurrentTime(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf("quelle heure", "quel heure", "il est quelle heure", "il fait quelle heure", "il fait quel heure")
            .any(normalized::contains)
    }
}
