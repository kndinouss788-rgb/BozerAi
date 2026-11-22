package ai.bozer.app

data class SecretaryTask(
    val id: Long,
    val title: String,
    val isCompleted: Boolean,
    val dueAtMillis: Long?,
    val createdAt: Long
)
