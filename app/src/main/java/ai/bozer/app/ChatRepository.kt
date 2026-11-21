package ai.bozer.app

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BrainMode(val label: String, val description: String) {
    AUTO("Automatique", "Utilise le cerveau local hors ligne et la passerelle BOZER gratuite lorsqu'Internet est disponible."),
    ONLINE("En ligne", "Utilise la passerelle distante BOZER. Une connexion Internet est nécessaire."),
    OFFLINE("Hors ligne", "Utilise le cerveau Qwen installé sur le téléphone, sans Internet.");

    companion object {
        fun from(value: String?): BrainMode = values().firstOrNull { it.name == value } ?: AUTO
    }
}

class ChatRepository(
    private val database: ChatDatabase,
    private val model: LocalLlamaEngine,
    private val internetSearch: AndroidPublicSearch = AndroidPublicSearch(),
    private val remoteBrain: RemoteBrainClient? = null
) {
    private val inferenceLock = Mutex()
    @Volatile var brainMode: BrainMode = BrainMode.AUTO
    @Volatile var internetAvailable: () -> Boolean = { true }
    val remoteConfigured: Boolean get() = remoteBrain?.isConfigured == true
    fun history(): List<ChatMessage> = database.history()
    fun clearHistory() = database.clearHistory()
    fun save(message: ChatMessage) = database.save(message)
    fun rememberedFacts(): List<String> = database.rememberedFacts()
    fun clearRememberedFacts() = database.clearRememberedFacts()

    suspend fun reply(history: List<ChatMessage>): String = inferenceLock.withLock {
        val latestUserText = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        if (asksToForgetMemory(latestUserText)) database.clearRememberedFacts()
        else extractExplicitMemory(latestUserText)?.let(database::rememberFact)

        if (asksForCurrentTime(latestUserText)) {
            return@withLock "Il est " + LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH 'h' mm", Locale.FRANCE)
            ) + "."
        }

        val onlineAvailable = internetAvailable()
        val selectedMode = brainMode
        val searchQuery = extractSearchQuery(latestUserText)
        if (searchQuery != null && (selectedMode == BrainMode.OFFLINE || !onlineAvailable)) {
            return@withLock "Je ne peux pas accéder à Internet actuellement. Tu es hors ligne ou le mode Hors ligne est activé. Reconnecte-toi puis repasse en Automatique ou En ligne pour que je fasse cette recherche."
        }
        if (selectedMode == BrainMode.ONLINE && !onlineAvailable) {
            return@withLock "Je ne peux pas accéder à Internet actuellement. Vérifie ta connexion, puis réessaie ou passe en Automatique pour utiliser le cerveau local."
        }
        val autoCanUseRemote = selectedMode == BrainMode.AUTO &&
            onlineAvailable && remoteBrain?.isConfigured == true
        if (selectedMode != BrainMode.ONLINE && !model.isLoaded && !autoCanUseRemote) {
            return@withLock "Le cerveau local BOZER intégré à l'application n'est pas encore prêt. Patiente pendant son initialisation puis réessaie."
        }
        if (selectedMode == BrainMode.ONLINE && remoteBrain?.isConfigured != true) {
            return@withLock "La passerelle distante BOZER n'est pas configurée. Repasse en Automatique pour utiliser le cerveau local gratuit."
        }

        val searchResults = if (searchQuery != null) {
            try {
                internetSearch.search(searchQuery, limit = 8)
            } catch (_: Exception) {
                return@withLock "Je n'ai pas pu effectuer la recherche Internet. La connexion ou les moteurs de recherche ne répondent pas ; je ne vais pas inventer de résultats."
            }
        } else emptyList()
        if (searchQuery != null && searchResults.isEmpty()) {
            return@withLock "La recherche Internet a bien été appelée, mais aucune source exploitable n'a été retournée. Je préfère ne pas inventer de résultats."
        }

        val prompt = BozerNeuralBrain.buildLocalPrompt(
            history, database.rememberedFacts().asReversed(), searchResults
        )
        val extraContext = BozerNeuralBrain.buildRemoteContext(
            database.rememberedFacts().takeLast(20), searchResults
        )
        try {
            // AUTO: use the configured free BOZER gateway when online, then fall back
            // to the bundled local Qwen brain. OFFLINE never makes a network request.
            val useRemote = selectedMode == BrainMode.ONLINE ||
                (selectedMode == BrainMode.AUTO && onlineAvailable && remoteBrain?.isConfigured == true)
            val answer = if (useRemote) {
                try {
                    remoteBrain!!.reply(history, extraContext)
                } catch (remoteError: Exception) {
                    if (selectedMode == BrainMode.ONLINE) {
                        "Le moteur distant BOZER n'est pas joignable : " +
                            (remoteError.message ?: "erreur réseau") +
                            ". Repasse en Automatique pour continuer avec le cerveau local."
                    } else {
                        model.generate(prompt)
                    }
                }
            } else {
                model.generate(prompt)
            }
            if (searchResults.isEmpty()) answer else buildString {
                append(answer)
                append("\n\nSources Internet (EYE_SEARCH) :\n")
                searchResults.forEachIndexed { index, result ->
                    append(index + 1).append(". ").append(result.title)
                        .append(" [").append(result.source).append("] — ")
                        .append(result.url).append('\n')
                }
            }
        } catch (error: Exception) {
            "Je rencontre un problème avec le cerveau local BOZER : " + (error.message ?: "erreur inconnue") + ". Réessaie dans un instant."
        }
    }

    private fun extractExplicitMemory(text: String): String? {
        val request = Regex(
            """^(?:s'il te plaît[, ]*)?(?:retiens|mémorise|memorise|souviens-toi)(?: cette information)?(?: pour la suite)?\s*[:,-]?\s*(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(text.trim()) ?: return null
        var fact = request.groupValues[1].trim()
        fact = fact.replace(Regex("""^(?:que|ceci\s*[:,-]?)\s*""", RegexOption.IGNORE_CASE), "")
        fact = fact.replace(
            Regex("""\s+(?:réponds|reponds|dis-moi|confirme)\b.*$""", RegexOption.IGNORE_CASE), ""
        ).trim().trimEnd('.', '!', '?', ':', ';')
        return fact.takeIf { it.length >= 3 }
    }

    /**
     * Explicit web requests and clearly time-sensitive questions use EYE_SEARCH.
     * Ordinary timeless conversation stays on the normal chat path.
     */
    private fun extractSearchQuery(text: String): String? {
        val clean = text.trim()
        val explicit = Regex(
            """^(?:(?:peux-tu|peux tu|est-ce que tu peux|j'aimerais que tu|je veux que tu)\s*)?(?:(?:s'il te plaît|s'il te plait)\s*)?(?:(?:fais|faire|lance|effectue)\s+)?(?:une\s+)?(?:recherche|recherches|rechercher|cherche|chercher|va\s+chercher|trouve|vérifie|verifie|regarde)(?:\s+(?:moi|pour\s+moi))?(?:\s+sur\s+(?:internet|le\s+web))?\s*[:,-]?\s*(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(clean)
        if (explicit != null) {
            val query = explicit.groupValues[1].trim().removePrefix("sur ").trim()
            return query.takeIf { it.length >= 2 }
        }

        val normalized = clean.lowercase(Locale.ROOT)
        val freshnessSignals = listOf(
            "aujourd'hui", "aujourd’hui", "actuellement", "en ce moment",
            "dernières nouvelles", "dernieres nouvelles", "dernières informations",
            "dernieres informations", "dernières infos", "dernieres infos",
            "actualité", "actualite", "actualités", "actualites",
            "récent", "recent", "récente", "recente", "plus récent", "plus recente",
            "dernier", "dernière", "derniere", "à jour", "a jour",
            "mise à jour", "mise a jour", "prix", "taux de change", "cours actuel",
            "météo", "meteo", "cette semaine", "cette année", "cette annee",
            "résultat", "resultat", "score", "classement", "en 2026"
        )
        val asksForFreshFacts = freshnessSignals.any(normalized::contains) ||
            Regex("""\b(?:qui\s+est|quel\s+est|quelle\s+est)\b.*\b(?:actuel|actuelle|maintenant)\b""")
                .containsMatchIn(normalized)
        return clean.takeIf { asksForFreshFacts && it.length >= 5 }
    }
    private fun asksToForgetMemory(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf(
            "oublie tout ce que tu as mémorisé", "oublie tout ce que tu as memorise",
            "efface ta mémoire", "efface ta memoire", "oublie tes souvenirs"
        ).any(normalized::contains)
    }

    private fun asksForCurrentTime(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf("quelle heure", "quel heure", "il est quelle heure", "il fait quelle heure", "il fait quel heure")
            .any(normalized::contains)
    }

}