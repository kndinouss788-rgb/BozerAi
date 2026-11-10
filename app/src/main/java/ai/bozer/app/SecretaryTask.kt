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


sealed interface SecretaryCommand {
    data class Add(val title: String) : SecretaryCommand
    data class Complete(val title: String) : SecretaryCommand
    data class Delete(val title: String) : SecretaryCommand
    data object ListOpen : SecretaryCommand
}

object SecretaryCommandParser {
    fun parse(input: String): SecretaryCommand? {
        val text = input.trim().removeSuffix(".").removeSuffix("!").trim()
        val normalized = normalize(text)
        val add = Regex("^(?:bozer[, ]*)?(?:ajoute|ajouter|note|enregistre)(?:-moi)?\\s+(?:une tache|a ma liste|dans ma liste)\\s+(.+)$")
            .find(normalized)?.groupValues?.get(1)?.trim()
        if (!add.isNullOrBlank()) return SecretaryCommand.Add(text.substringAfterLast(" ").let {
            val originalTitle = Regex("(?i)(?:ajoute|ajouter|note|enregistre)(?:-moi)?\\s+(?:une tâche|une tache|à ma liste|a ma liste|dans ma liste)\\s+(.+)$")
                .find(text)?.groupValues?.get(1)?.trim()
            originalTitle ?: add
        })

        val complete = Regex("^(?:termine|terminer|acheve|achever|coche)\\s+(?:la tache\\s+)?(.+)$")
            .find(normalized)?.groupValues?.get(1)?.trim()
        if (!complete.isNullOrBlank()) return SecretaryCommand.Complete(complete)

        val delete = Regex("^(?:supprime|supprimer|efface|effacer)\\s+(?:la tache\\s+)?(.+)$")
            .find(normalized)?.groupValues?.get(1)?.trim()
        if (!delete.isNullOrBlank()) return SecretaryCommand.Delete(delete)

        if (normalized in setOf("mes taches", "liste mes taches", "montre mes taches", "affiche mes taches", "liste les taches")) {
            return SecretaryCommand.ListOpen
        }
        return null
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}

class SecretaryCommandProcessor(private val repository: SecretaryRepository) {
    fun execute(input: String): String? = when (val command = SecretaryCommandParser.parse(input)) {
        null -> null
        is SecretaryCommand.Add -> {
            val id = repository.createTask(command.title)
            "C'est noté. J'ai ajouté « ${command.title} » à tes tâches (n°$id)."
        }
        is SecretaryCommand.Complete -> {
            val task = repository.tasks().firstOrNull { it.title.equals(command.title, ignoreCase = true) }
            if (task == null) "Je ne trouve pas la tâche « ${command.title} »."
            else if (repository.completeTask(task.id)) "C'est fait : « ${task.title} » est marquée comme terminée."
            else "Je n'ai pas réussi à mettre à jour cette tâche."
        }
        is SecretaryCommand.Delete -> {
            val task = repository.tasks().firstOrNull { it.title.equals(command.title, ignoreCase = true) }
            if (task == null) "Je ne trouve pas la tâche « ${command.title} »."
            else if (repository.deleteTask(task.id)) "J'ai supprimé la tâche « ${task.title} »."
            else "Je n'ai pas réussi à supprimer cette tâche."
        }
        SecretaryCommand.ListOpen -> {
            val open = repository.tasks(includeCompleted = false)
            if (open.isEmpty()) "Tu n'as aucune tâche en attente."
            else "Voici tes tâches en attente :\\n" + open.joinToString("\\n") { "• ${it.title}" }
        }
    }
}
