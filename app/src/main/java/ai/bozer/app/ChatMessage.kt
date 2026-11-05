package ai.bozer.app

data class ChatMessage(
    val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
