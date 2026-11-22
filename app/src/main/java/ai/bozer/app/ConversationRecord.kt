package ai.bozer.app

data class ConversationRecord(
    val id: Long,
    val title: String,
    val preview: String,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)
