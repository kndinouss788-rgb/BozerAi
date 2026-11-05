package ai.bozer.app

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
        val transcript = buildString {
            history.takeLast(12).forEach { item ->
                when (item.role) {
                    "user" -> append("Utilisateur : ").append(item.content).append('\n')
                    "assistant" -> append("BOZER : ").append(item.content).append('\n')
                }
            }
            append("Réponds au dernier message de l'utilisateur.")
        }
        try {
            model.generate(transcript)
        } catch (error: Exception) {
            "Je n'ai pas pu générer cette réponse avec mon moteur local : ${error.message ?: "erreur inconnue"}. Réessaie après avoir vérifié que le modèle est chargé."
        }
    }
}
