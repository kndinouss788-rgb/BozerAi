package ai.bozer.app

class ChatRepository(
    private val database: ChatDatabase,
    private val model: LocalLlamaClient
) {
    fun history(): List<ChatMessage> = database.history()
    fun save(message: ChatMessage) = database.save(message)

    fun reply(history: List<ChatMessage>): String = try {
        model.complete(history)
    } catch (_: Exception) {
        "Je n'arrive pas encore à joindre mon cerveau local. Le chat et son historique fonctionnent, mais le moteur IA Android n'est pas encore démarré sur cet appareil."
    }
}
