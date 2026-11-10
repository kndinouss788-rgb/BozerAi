package ai.bozer.app

enum class SecretaryPriority(val level: Int, val label: String) {
    LOW(0, "Basse"),
    NORMAL(1, "Normale"),
    HIGH(2, "Haute");

    companion object {
        fun fromLevel(level: Int): SecretaryPriority =
            entries.firstOrNull { it.level == level } ?: NORMAL
    }
}

data class SecretaryTask(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dueAtMillis: Long? = null,
    val priority: SecretaryPriority = SecretaryPriority.NORMAL,
    val completed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    init {
        require(id >= 0) { "L'identifiant d'une tâche ne peut pas être négatif." }
        require(title.isNotBlank()) { "Une tâche doit avoir un titre." }
        require(title.trim().length <= 140) { "Le titre est limité à 140 caractères." }
        require(notes.length <= 2000) { "Les notes sont limitées à 2 000 caractères." }
        require(dueAtMillis == null || dueAtMillis >= 0) { "La date limite est invalide." }
    }
}

/** Persistence-facing API. Android reminder scheduling is a separate integration. */
class SecretaryRepository(private val database: ChatDatabase) {
    fun createTask(
        title: String,
        notes: String = "",
        dueAtMillis: Long? = null,
        priority: SecretaryPriority = SecretaryPriority.NORMAL
    ): Long = database.saveSecretaryTask(
        SecretaryTask(title = title.trim(), notes = notes, dueAtMillis = dueAtMillis, priority = priority)
    )

    fun updateTask(task: SecretaryTask): Long = database.saveSecretaryTask(task)
    fun tasks(includeCompleted: Boolean = true): List<SecretaryTask> =
        database.secretaryTasks(includeCompleted)
    fun completeTask(taskId: Long): Boolean = database.setSecretaryTaskCompleted(taskId, true)
    fun reopenTask(taskId: Long): Boolean = database.setSecretaryTaskCompleted(taskId, false)
    fun deleteTask(taskId: Long): Boolean = database.deleteSecretaryTask(taskId)
}
