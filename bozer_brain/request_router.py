"""High-level request classification for the BOZER decision core.

This layer classifies intent; it never calls a model, searches the web, or executes
phone actions. External capabilities must be explicitly invoked by a later,
authorized orchestration layer.
"""
from dataclasses import dataclass
from enum import Enum
import re

from .intent import parse_intent
from .models import ActionProposal, IntentKind
from .planner import plan_action


class RequestRoute(str, Enum):
    CHAT = "chat"
    RESEARCH = "research"
    DEVICE_ACTION = "device_action"
    MULTI_STEP = "multi_step"
    CLARIFICATION = "clarification"


@dataclass(frozen=True)
class RequestAnalysis:
    route: RequestRoute
    confidence: float
    summary: str
    intent_kind: IntentKind
    action_proposal: ActionProposal
    requires_user_input: bool
    reasons: tuple[str, ...]


_RESEARCH = re.compile(
    r"\\b(recherche|cherche|trouve|vérifie|verifie|compare|actualit[eé]s?|"
    r"sur internet|sur le web|sources?|informations? récentes?)\\b",
    re.IGNORECASE,
)
_ACTION_HINT = re.compile(
    r"\\b(ouvre|lance|appelle|téléphone|telephone|envoie|envoi|programme|"
    r"règle|regle|mets|met|crée|cree|supprime|partage|ajoute)\\b",
    re.IGNORECASE,
)
_MULTI_CONNECTOR = re.compile(r"\\b(puis|ensuite|après|apres|et enfin)\\b", re.IGNORECASE)
_ACTION_VERBS = re.compile(
    r"\\b(ouvre|lance|appelle|téléphone|telephone|envoie|envoi|programme|"
    r"règle|regle|mets|met|crée|cree|supprime|partage|ajoute)\\b",
    re.IGNORECASE,
)


def analyze_request(message: str) -> RequestAnalysis:
    """Classify a user request into a safe next route without performing it."""
    text = (message or "").strip()
    if not text:
        raise ValueError("message must not be empty")

    intent = parse_intent(text)
    proposal = plan_action(intent)
    reasons = []

    verbs = _ACTION_VERBS.findall(text)
    # Never collapse two action verbs into one target, even when the user joins
    # them with an unsupported connector such as "et". The decision engine will
    # decompose supported separators or fail closed with a clarification.
    if len(verbs) >= 2:
        reasons.append("multiple_action_verbs_detected")
        if _MULTI_CONNECTOR.search(text):
            reasons.append("sequential_connector_detected")
        else:
            reasons.append("unsupported_or_missing_sequence_separator")
        reasons.append("planning_only_no_execution")
        return RequestAnalysis(
            RequestRoute.MULTI_STEP, 0.78,
            "Demande contenant plusieurs actions à décomposer ou à faire préciser.",
            intent.kind, proposal, False, tuple(reasons),
        )

    if proposal.safe_to_execute:
        return RequestAnalysis(
            RequestRoute.DEVICE_ACTION, max(intent.confidence, 0.7),
            "Action téléphone reconnue ; proposition à soumettre aux contrôles Android.",
            intent.kind, proposal, False,
            ("supported_explicit_intent", "android_permission_and_confirmation_still_apply"),
        )

    if _RESEARCH.search(text):
        return RequestAnalysis(
            RequestRoute.RESEARCH, 0.82,
            "Demande de recherche détectée ; aucune recherche n'est lancée automatiquement.",
            intent.kind, proposal, False,
            ("research_language_detected", "external_search_requires_explicit_opt_in"),
        )

    if _ACTION_HINT.search(text):
        reasons.append("action_language_detected_but_no_supported_complete_parameters")
        return RequestAnalysis(
            RequestRoute.CLARIFICATION, 0.62,
            "Une action semble demandée, mais ses paramètres sont incomplets ou non pris en charge.",
            intent.kind, proposal, True, tuple(reasons),
        )

    return RequestAnalysis(
        RequestRoute.CHAT, 0.70,
        "Conversation générale ; aucune action téléphone n'est autorisée par cette analyse.",
        intent.kind, proposal, False,
        ("no_action_or_research_intent_detected",),
    )
