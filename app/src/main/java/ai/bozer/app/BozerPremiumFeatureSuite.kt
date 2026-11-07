package ai.bozer.app

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.text.Normalizer

/**
 * Product foundation for BOZER's seven optional professional capabilities.
 *
 * Security rules:
 * - a feature pass is never granted by a UI flag or a client-side price check;
 * - payment must be verified by a trusted backend before a pass is issued;
 * - this file creates plans and typed requests, it does not pretend that external
 *   actions (sending messages, purchases, calls, etc.) have happened.
 */
enum class BozerPremiumFeature(
    val displayName: String,
    val promise: String,
    val priceLabel: String = "1 € / 24 h"
) {
    BUSINESS("BOZER BUSINESS", "Clients, orders, stock, quotes and sales tracking"),
    AGENT("BOZER AGENT", "Break a goal into safe, confirmable phone tasks"),
    RESEARCH("BOZER RESEARCH", "Research, compare sources and prepare a cited brief"),
    DOCUMENTS("BOZER DOCUMENTS", "Prepare quotes, invoices, CVs and PDF-ready documents"),
    SECRETARY("BOZER SECRETARY", "Plan days, appointments, reminders and follow-ups"),
    TRANSLATOR("BOZER TRANSLATOR", "Translate written and spoken conversations"),
    DEVELOPER("BOZER DEVELOPER", "Explain code, diagnose errors and propose changes")
}

data class BozerFeaturePass(
    val userId: String,
    val feature: BozerPremiumFeature,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val receiptId: String
) {
    fun isActive(now: Instant): Boolean =
        userId.isNotBlank() &&
            receiptId.isNotBlank() &&
            expiresAt.isAfter(now) &&
            !issuedAt.isAfter(now) &&
            Duration.between(issuedAt, expiresAt) <= Duration.ofHours(24).plusMinutes(1)
}

/** Only a trusted server-side payment verifier should create this object. */
fun interface BozerPassVerifier {
    suspend fun verifyAndIssue(userId: String, feature: BozerPremiumFeature, paymentReference: String): BozerFeaturePass?
}

data class FeatureStep(
    val title: String,
    val detail: String,
    val requiresUserConfirmation: Boolean = false
)

data class FeatureWorkflow(
    val feature: BozerPremiumFeature,
    val userGoal: String,
    val steps: List<FeatureStep>,
    val safetyNote: String
)

data class FeatureRequest(
    val feature: BozerPremiumFeature,
    val originalText: String,
    val goal: String
)

/**
 * Conservative command detector. It identifies which specialist should help;
 * it never performs a sensitive action based on a guessed intent.
 */
object BozerFeatureIntentDetector {
    fun detect(text: String): FeatureRequest? {
        val input = normalize(text)
        if (input.isBlank()) return null

        val candidates = listOf(
            BozerPremiumFeature.BUSINESS to listOf("client", "commande", "stock", "vente", "chiffre d'affaires", "devis commercial", "mon commerce", "facture client"),
            BozerPremiumFeature.AGENT to listOf("fais ces tâches", "execute ce plan", "exécute ce plan", "organise et fais", "agent personnel", "automatise"),
            BozerPremiumFeature.RESEARCH to listOf("recherche approfondie", "compare les sources", "rapport de recherche", "étude de marché", "cherche plusieurs sources"),
            BozerPremiumFeature.DOCUMENTS to listOf("crée un devis", "cree un devis", "crée une facture", "cree une facture", "rédige mon cv", "redige mon cv", "document pdf"),
            BozerPremiumFeature.SECRETARY to listOf("organise ma journée", "organise ma journee", "mes rendez-vous", "mes rendez vous", "planning de la semaine", "rappelle-moi"),
            BozerPremiumFeature.TRANSLATOR to listOf("traduis", "traduction", "traducteur", "interprète", "interprete"),
            BozerPremiumFeature.DEVELOPER to listOf("corrige mon code", "débogue", "debug", "analyse ce code", "développe cette fonction", "developpe cette fonction")
        )
        val match = candidates.firstOrNull { (_, phrases) -> phrases.any { normalize(it) in input } } ?: return null
        return FeatureRequest(match.first, text.trim(), text.trim())
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Produces transparent workflows for the seven capabilities.
 * Real integrations are injected separately; missing integrations must be
 * reported as unavailable rather than simulated.
 */
class BozerPremiumFeatureCoordinator(
    private val clock: Clock = Clock.systemUTC()
) {
    fun hasAccess(userId: String, feature: BozerPremiumFeature, passes: List<BozerFeaturePass>): Boolean {
        val now = clock.instant()
        return passes.any { it.userId == userId && it.feature == feature && it.isActive(now) }
    }

    fun createWorkflow(request: FeatureRequest): FeatureWorkflow {
        val steps = when (request.feature) {
            BozerPremiumFeature.BUSINESS -> listOf(
                FeatureStep("Clarifier l'activité", "Identifier le commerce, les produits ou services et la période concernée."),
                FeatureStep("Structurer les données", "Préparer les fiches clients, commandes, stocks, ventes et dépenses à partir des informations fournies."),
                FeatureStep("Calculer et vérifier", "Calculer les totaux et signaler les données manquantes avant tout bilan."),
                FeatureStep("Préparer les documents", "Générer un devis, une facture ou une relance en brouillon."),
                FeatureStep("Valider", "Faire vérifier les montants et obtenir l'accord de l'utilisateur avant tout envoi ou changement.")
            )
            BozerPremiumFeature.AGENT -> listOf(
                FeatureStep("Comprendre l'objectif", "Reformuler le résultat attendu et les contraintes."),
                FeatureStep("Découper le travail", "Créer des étapes courtes, ordonnées et vérifiables."),
                FeatureStep("Vérifier les permissions", "Identifier les applications et autorisations nécessaires."),
                FeatureStep("Exécuter les étapes autorisées", "Réaliser uniquement les actions effectivement prises en charge par l'appareil.", true),
                FeatureStep("Rendre compte", "Lister ce qui a réussi, échoué ou attend une intervention.")
            )
            BozerPremiumFeature.RESEARCH -> listOf(
                FeatureStep("Définir la question", "Fixer le sujet, la zone géographique et la période."),
                FeatureStep("Chercher", "Interroger les sources Web réellement disponibles."),
                FeatureStep("Croiser", "Comparer les dates, les affirmations et les sources indépendantes."),
                FeatureStep("Rédiger", "Préparer une synthèse avec liens et limites des informations.")
            )
            BozerPremiumFeature.DOCUMENTS -> listOf(
                FeatureStep("Collecter les informations", "Demander les champs nécessaires et signaler ceux qui manquent."),
                FeatureStep("Préparer le brouillon", "Structurer le devis, la facture, le CV ou la lettre."),
                FeatureStep("Vérifier", "Contrôler les montants, dates, coordonnées et formulations."),
                FeatureStep("Exporter", "Produire un fichier seulement lorsqu'un générateur de document est connecté.")
            )
            BozerPremiumFeature.SECRETARY -> listOf(
                FeatureStep("Recueillir les priorités", "Identifier les tâches, rendez-vous, échéances et disponibilités."),
                FeatureStep("Organiser", "Construire un planning réaliste avec des marges."),
                FeatureStep("Préparer les rappels", "Proposer les rappels et demander l'autorisation Android nécessaire.", true),
                FeatureStep("Faire le suivi", "Mettre à jour les tâches selon les confirmations de l'utilisateur.")
            )
            BozerPremiumFeature.TRANSLATOR -> listOf(
                FeatureStep("Identifier les langues", "Détecter ou demander la langue source et la langue cible."),
                FeatureStep("Traduire", "Préserver le sens, le ton et les noms propres."),
                FeatureStep("Adapter", "Proposer une formulation naturelle ou professionnelle."),
                FeatureStep("Voix", "La traduction audio en direct nécessite une intégration de reconnaissance et de synthèse vocales.")
            )
            BozerPremiumFeature.DEVELOPER -> listOf(
                FeatureStep("Analyser le contexte", "Identifier le langage, le framework et le résultat attendu."),
                FeatureStep("Diagnostiquer", "Repérer les erreurs et expliquer leur cause probable."),
                FeatureStep("Proposer un correctif", "Préparer une modification limitée et lisible."),
                FeatureStep("Tester", "Exécuter les tests disponibles et rapporter leurs résultats réels.")
            )
        }
        return FeatureWorkflow(
            feature = request.feature,
            userGoal = request.goal,
            steps = steps,
            safetyNote = "BOZER ne doit jamais prétendre avoir envoyé, payé, réservé, modifié ou exécuté quelque chose sans confirmation technique réelle."
        )
    }

    fun accessMessage(feature: BozerPremiumFeature, hasAccess: Boolean): String =
        if (hasAccess) "Accès actif : ${feature.displayName}."
        else "Accès non actif : ${feature.displayName}. Un paiement vérifié par le serveur est nécessaire. Aucun achat n'est effectué dans cette version."
}
