package ai.bozer.app

data class ActionLogEntry(
    val id: Long = 0,
    val actionType: String,
    val summary: String,
    val status: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis()
)
