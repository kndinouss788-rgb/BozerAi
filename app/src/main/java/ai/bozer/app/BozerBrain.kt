package ai.bozer.app

import java.util.Locale

/**
 * The first decision layer between natural-language input and BOZER's
 * conversation or Android tools. It only routes capabilities that exist.
 */
sealed interface BozerIntent {
    data class AndroidAction(val action: PhoneAction) : BozerIntent
    data class PublicResearch(val query: String) : BozerIntent
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
        BozerToolDefinition("public_search", "Recherche publique Wikipédia", BozerToolState.AVAILABLE, false),
        BozerToolDefinition("android_action", "Actions Android de base", BozerToolState.AVAILABLE, true),
        BozerToolDefinition("agent_workflows", "Agent multi-étapes", BozerToolState.IN_DEVELOPMENT, true),
        BozerToolDefinition("documents", "Documents PDF/DOCX", BozerToolState.PLANNED, false),
        BozerToolDefinition("business", "Business", BozerToolState.PLANNED, false),
        BozerToolDefinition("translation", "Traduction spécialisée", BozerToolState.PLANNED, false),
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
        extractResearchQuery(text)?.let { return BozerIntent.PublicResearch(it) }
        return BozerIntent.Conversation(text)
    }

    fun plan(text: String): BozerTaskPlan = taskPlanner.plan(classify(text))

    suspend fun respond(history: List<ChatMessage>, intent: BozerIntent): String {
        // Search is still executed inside ChatRepository so source retrieval,
        // untrusted-content handling and local/online routing stay centralized.
        return repository.reply(history)
    }

    private fun extractResearchQuery(text: String): String? {
        val normalized = text.trim()
        val patterns = listOf(
            Regex("""^(?:fais\s+une\s+)?(?:recherche|cherche)(?:\s+sur\s+internet)?\s*[:,-]?\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:trouve|vérifie|verifie)\s+(?:sur\s+internet|sur\s+le\s+web)\s*[:,-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.length >= 2 }
        }
    }
}
