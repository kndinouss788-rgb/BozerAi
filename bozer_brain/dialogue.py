"""Conversation orchestration boundary for BOZER."""

from dataclasses import dataclass
from typing import Callable, Optional

from .context import build_context
from .eye_search import EyeSearch, ResearchContext
from .intent import parse_intent
from .memory import ConversationMemory
from .model_gateway import ModelGateway, local_llama_provider_from_env, openai_provider_from_env
from .models import ActionProposal, IntentKind
from .planner import plan_action


@dataclass(frozen=True)
class DialogueResult:
    reply: str
    proposal: ActionProposal
    used_language_model: bool
    model_error: Optional[str] = None
    research: Optional[ResearchContext] = None


class DialogueEngine:
    def __init__(
        self,
        memory: ConversationMemory,
        responder: Optional[Callable[[str], str]] = None,
        history_limit: int = 8,
        model_gateway: Optional[ModelGateway] = None,
        eye_search: Optional[EyeSearch] = None,
    ):
        if history_limit < 0:
            raise ValueError("history_limit must be non-negative")
        self.memory = memory
        self.responder = responder
        self.history_limit = history_limit
        self.model_gateway = model_gateway
        self.eye_search = eye_search

    @classmethod
    def from_environment(cls, memory, *, responder=None, history_limit=8,
                         eye_search=None, environ=None):
        """Create the dialogue brain with a server-configured real model provider."""
        # Prefer a free on-device model. Use a remote provider only when no local endpoint is configured.
        provider = local_llama_provider_from_env(environ) or openai_provider_from_env(environ)
        return cls(
            memory,
            responder=responder,
            history_limit=history_limit,
            model_gateway=ModelGateway(provider),
            eye_search=eye_search,
        )

    def respond(
        self,
        user_text: str,
        *,
        allow_model: bool = False,
        allow_search: bool = False,
    ) -> DialogueResult:
        text = (user_text or "").strip()
        if not text:
            raise ValueError("user_text must not be empty")

        intent = parse_intent(text)
        proposal = plan_action(intent)
        model_error = None
        research = None

        if proposal.safe_to_execute:
            reply = self._action_reply(intent.kind, proposal.requires_confirmation)
            used_model = False
        else:
            context = build_context(self.memory, text, history_limit=self.history_limit)
            prompt = context.prompt

            # Research is an explicit, separate opt-in and never runs for phone actions.
            if allow_search and self.eye_search is not None:
                research = self.eye_search.search(text, enabled=True)
                prompt = prompt + "\n\n" + research.prompt

            if self.model_gateway is not None:
                result = self.model_gateway.generate(prompt, enabled=allow_model)
                if result.ok:
                    reply = result.text
                    used_model = True
                else:
                    reply = self._model_unavailable_reply()
                    used_model = False
                    model_error = result.error
            elif self.responder is not None:
                if not allow_model:
                    reply = self._model_unavailable_reply()
                    used_model = False
                    model_error = "provider_use_not_enabled"
                else:
                    reply = self.responder(prompt)
                    if not isinstance(reply, str) or not reply.strip():
                        raise ValueError("responder must return a non-empty string")
                    used_model = True
            else:
                reply = self._model_unavailable_reply()
                used_model = False
                model_error = "provider_not_configured"

        self.memory.add_turn("user", text)
        self.memory.add_turn("assistant", reply)
        return DialogueResult(reply, proposal, used_model, model_error, research)

    @staticmethod
    def _model_unavailable_reply() -> str:
        return (
            "Je peux conserver notre échange et préparer certaines actions, "
            "mais mon moteur de réponse IA n'est pas encore connecté."
        )

    @staticmethod
    def _action_reply(kind: IntentKind, confirmation: bool) -> str:
        if confirmation:
            return "J'ai préparé cette action. Elle nécessitera ta confirmation avant exécution."
        return (
            "J'ai reconnu ta demande et préparé une proposition. "
            "Android devra vérifier les autorisations avant toute exécution."
        )
