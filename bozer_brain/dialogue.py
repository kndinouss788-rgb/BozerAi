"""Conversation orchestration boundary for BOZER.

Without a configured language-model responder, this module is intentionally honest:
it can route explicit actions and maintain history, but it does not pretend to be an
open-domain AI model.
"""
from dataclasses import dataclass
from typing import Callable, Optional

from .intent import parse_intent
from .memory import ConversationMemory
from .models import ActionProposal, IntentKind
from .planner import plan_action


@dataclass(frozen=True)
class DialogueResult:
    reply: str
    proposal: ActionProposal
    used_language_model: bool


class DialogueEngine:
    def __init__(
        self,
        memory: ConversationMemory,
        responder: Optional[Callable[[str], str]] = None,
    ):
        self.memory = memory
        self.responder = responder

    def respond(self, user_text: str) -> DialogueResult:
        text = (user_text or "").strip()
        if not text:
            raise ValueError("user_text must not be empty")

        intent = parse_intent(text)
        proposal = plan_action(intent)

        if proposal.safe_to_execute:
            reply = self._action_reply(intent.kind, proposal.requires_confirmation)
            used_model = False
        elif self.responder is not None:
            # The responder is an injected integration; credentials and network
            # configuration belong outside this core.
            reply = self.responder(text)
            if not isinstance(reply, str) or not reply.strip():
                raise ValueError("responder must return a non-empty string")
            used_model = True
        else:
            reply = (
                "Je peux conserver notre échange et préparer certaines actions, "
                "mais mon moteur de réponse IA n'est pas encore connecté."
            )
            used_model = False

        self.memory.add_turn("user", text)
        self.memory.add_turn("assistant", reply)
        return DialogueResult(reply, proposal, used_model)

    @staticmethod
    def _action_reply(kind: IntentKind, confirmation: bool) -> str:
        if confirmation:
            return "J'ai préparé cette action. Elle nécessitera ta confirmation avant exécution."
        return (
            "J'ai reconnu ta demande et préparé une proposition. "
            "Android devra vérifier les autorisations avant toute exécution."
        )
