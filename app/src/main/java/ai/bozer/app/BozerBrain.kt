package ai.bozer.app

import java.util.Locale

/**
 * The first decision layer between natural-language input and BOZER's
 * conversation or Android tools. It only routes capabilities that exist.
 */
sealed interface BozerIntent {
    data class AndroidAction(val action: PhoneAction) : BozerIntent
    data class PublicResearch(val query: String) : BozerIntent
    data class Translation(val targetLanguage: String, val sourceText: String) : BozerIntent
    data class SecretaryAddTask(val title: String) : BozerIntent
    data object SecretaryListTasks : BozerIntent
    data class Conversation(val text: String) : BozerIntent
}

enum class BozerToolState { AVAILABLE, IN_DEVELOPMENT, PLANNED }

data class BozerToolDefinition(
    val id: String,
    val label: String,
    val state: BozerToolState,
    val confirmationRequired: Boolean
)

/**
 * Capability registry. A tool is marked AVAILABLE only when an actual
 * implementation exists in this Android application.
 */
class BozerToolRegistry {
    private val registered = listOf(
        BozerToolDefinition("local_chat", "Conversation locale", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("memory", "Souvenirs explicites", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("public_search", "Recherche publique multi-sources", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("android_action", "Actions Android de base", BozerToolState.AVAILABLE, true),
        BozerToolDefinition("secretary", "Liste de tâches locale", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("agent_workflows", "Agent multi-étapes", BozerToolState.PLANNED, true),
        BozerToolDefinition("documents", "Documents PDF/DOCX", BozerToolState.PLANNED, false),
        BozerToolDefinition("business", "Business", BozerToolState.PLANNED, false),
        BozerToolDefinition("translation", "Traduction spécialisée", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("developer", "Développement logiciel intégré", BozerToolState.PLANNED, true)
    )

    fun all(): List<BozerToolDefinition> = registered.toList()
    fun available(): List<BozerToolDefinition> = registered.filter { it.state == BozerToolState.AVAILABLE }
    fun find(id: String): BozerToolDefinition? = registered.firstOrNull { it.id == id }
}

data class BozerTaskStep(
    val order: Int,
    val label: String,
    val requiresConfirmation: Boolean,
    val successEvidence: String
)

data class BozerTaskPlan(
    val intent: BozerIntent,
    val steps: List<BozerTaskStep>
)

/**
 * Converts a classified intention into a small, auditable execution plan.
 * The plan describes required checks; it never marks a step successful by itself.
 */
class BozerTaskPlanner {
    fun plan(intent: BozerIntent): BozerTaskPlan = when (intent) {
        is BozerIntent.AndroidAction -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Vérifier les paramètres et l'application cible", false, "Intent Android résolu"),
                BozerTaskStep(2, "Présenter l'action à l'utilisateur", true, "Confirmation explicite reçue"),
                BozerTaskStep(3, "Exécuter l'action autorisée", false, "startActivity accepté par Android"),
                BozerTaskStep(4, "Présenter le résultat réel", false, "Résultat Android enregistré")
            )
        )
        is BozerIntent.PublicResearch -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Interroger la source publique", false, "Résultats réellement reçus"),
                BozerTaskStep(2, "Traiter les extraits comme des données non fiables", false, "Sources limitées et nettoyées"),
                BozerTaskStep(3, "Rédiger une réponse", false, "Synthèse produite avec le contexte"),
                BozerTaskStep(4, "Afficher les liens consultés", false, "URLs présentes dans la réponse")
            )
        )
        is BozerIntent.Translation -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Identifier la langue cible et le texte source", false, "Langue et texte non vides"),
                BozerTaskStep(2, "Exécuter le traducteur avec le cerveau choisi", false, "Texte traduit reçu"),
                BozerTaskStep(3, "Conserver le sens et le ton sans ajouter de commentaire", false, "Traduction non vide affichée")
            )
        )
        is BozerIntent.SecretaryAddTask -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Valider le titre de la tâche", false, "Titre non vide"),
                BozerTaskStep(2, "Enregistrer la tâche dans SQLite", false, "Identifiant de tâche retourné"),
                BozerTaskStep(3, "Confirmer l'enregistrement réel", false, "Tâche relue depuis SQLite")
            )
        )
        is BozerIntent.SecretaryListTasks -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Lire la liste locale", false, "Résultats retournés par SQLite"),
                BozerTaskStep(2, "Présenter les états réels", false, "Tâches actives et terminées affichées")
            )
        )
        is BozerIntent.Conversation -> BozerTaskPlan(
            intent,
            listOf(
                BozerTaskStep(1, "Comprendre le message et le contexte récent", false, "Derniers tours et souvenirs pertinents sélectionnés"),
                BozerTaskStep(2, "Produire une réponse avec le cerveau choisi", false, "Texte non vide reçu du moteur"),
                BozerTaskStep(3, "Enregistrer le tour dans l'historique local", false, "Insertion SQLite effectuée")
            )
        )
    }
}

class BozerOrchestrator(
    private val repository: ChatRepository,
    val tools: BozerToolRegistry = BozerToolRegistry(),
    val taskPlanner: BozerTaskPlanner = BozerTaskPlanner()
) {
    fun classify(text: String): BozerIntent {
        PhoneActionPlanner.parse(text)?.let { return BozerIntent.AndroidAction(it) }
        extractSecretaryTask(text)?.let { return it }
        extractTranslation(text)?.let { return it }
        if (isSecretaryListRequest(text)) return BozerIntent.SecretaryListTasks
        extractResearchQuery(text)?.let { return BozerIntent.PublicResearch(it) }
        return BozerIntent.Conversation(text)
    }

    fun plan(text: String): BozerTaskPlan = taskPlanner.plan(classify(text))

    suspend fun respond(history: List<ChatMessage>, intent: BozerIntent): String {
        return when (intent) {
            is BozerIntent.Translation ->
                repository.translate(intent.sourceText, intent.targetLanguage)
            is BozerIntent.SecretaryAddTask -> {
                val id = repository.addSecretaryTask(intent.title)
                if (id > 0L) {
                    val saved = repository.secretaryTasks().firstOrNull { it.id == id }
                    if (saved != null) "C'est noté. J'ai ajouté « ${saved.title} » à ta liste SECRETARY."
                    else "La tâche a reçu l'identifiant $id, mais je n'ai pas pu la relire. Vérifie la liste SECRETARY."
                } else {
                    "Je n'ai pas pu enregistrer cette tâche. Le titre est vide ou le stockage a refusé l'écriture."
                }
            }
            BozerIntent.SecretaryListTasks -> {
                val tasks = repository.secretaryTasks()
                if (tasks.isEmpty()) {
                    "Ta liste SECRETARY est vide. Tu peux dire : « Ajoute à ma liste : … »."
                } else {
                    buildString {
                        append("Voici ta liste SECRETARY :\n")
                        tasks.forEachIndexed { index, task ->
                            append(index + 1).append(". ")
                                .append(if (task.isCompleted) "✓ " else "• ")
                                .append(task.title)
                                .append(if (task.isCompleted) " — terminée" else " — à faire")
                                .append('\n')
                        }
                    }.trim()
                }
            }
            is BozerIntent.AndroidAction -> "Cette action doit passer par le parcours de confirmation Android."
            is BozerIntent.PublicResearch, is BozerIntent.Conversation ->
                // Search retrieval, untrusted-content handling and brain routing
                // remain centralized in ChatRepository.
                repository.reply(history)
        }
    }

    private fun extractSecretaryTask(text: String): BozerIntent.SecretaryAddTask? {
        val match = Regex(
            """^(?:ajoute|ajouter|note|inscris)\s+(?:à\s+ma\s+liste|une\s+tâche|une\s+tache|comme\s+tâche)\s*[:,-]?\s*(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim()) ?: return null
        val title = match.groupValues[1].trim().trimEnd('.', '!', '?')
        return title.takeIf { it.length in 2..180 }?.let(BozerIntent::SecretaryAddTask)
    }

    private fun isSecretaryListRequest(text: String): Boolean =
        Regex(
            """^(?:montre|affiche|liste|quelles\s+sont)(?:[-\s]+moi)?\s+(?:mes\s+)?(?:tâches|taches|choses\s+à\s+faire|ma\s+liste)\??$""",
            RegexOption.IGNORE_CASE
        ).matches(text.trim())

    private fun extractTranslation(text: String): BozerIntent.Translation? {
        val normalized = text.trim()
        val targetFirst = Regex(
            """^(?:traduis|traduire|translate)(?:\s+(?:ce\s+texte|moi))?\s+(?:en|vers)\s+([\p{L}][\p{L}-]{1,39})\s*[:,-]\s*(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (targetFirst != null) {
            val target = targetFirst.groupValues[1].trim()
            val source = targetFirst.groupValues[2].trim()
            return if (target.isNotBlank() && source.isNotBlank()) {
                BozerIntent.Translation(target, source)
            } else null
        }

        val targetLast = Regex(
            """^(?:traduis|traduire|translate)\s+(.+?)\s+(?:en|vers)\s+([\p{L}][\p{L}-]{1,39})$""",
            RegexOption.IGNORE_CASE
        ).find(normalized) ?: return null
        val source = targetLast.groupValues[1].trim()
        val target = targetLast.groupValues[2].trim()
        return if (target.isNotBlank() && source.isNotBlank()) {
            BozerIntent.Translation(target, source)
        } else null
    }


    private fun extractResearchQuery(text: String): String? {
        val normalized = text.trim()
        val patterns = listOf(
            Regex("""^(?:fais\s+une\s+)?(?:recherche|cherche)(?:\s+sur\s+(?:internet|le\s+web))?\s*[:,-]?\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:trouve|vérifie|verifie)\s+(?:sur\s+internet|sur\s+le\s+web)\s*[:,-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.length >= 2 }
        }
    }
}
