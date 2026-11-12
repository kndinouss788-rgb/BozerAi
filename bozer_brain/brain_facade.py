"""Top-level BOZER brain facade combining dialogue and safe decisions.

The facade delegates ordinary conversation to the existing DialogueEngine and
routes device actions through the deterministic decision pipeline. Optional
model mission reasoning is opt-in and cannot bypass local authorization gates.
It does not execute Android actions.
"""
from dataclasses import dataclass
from typing import FrozenSet, Optional

from .decision_engine import BozerDecisionEngine, BrainDecision
from .dialogue import DialogueEngine, DialogueResult
from .mission_reasoner import StructuredMissionReasoner
from .request_router import RequestRoute
from .tools import ToolRegistry


@dataclass(frozen=True)
class BrainResponse:
    reply: str
    decision: BrainDecision
    dialogue: Optional[DialogueResult]


class BozerBrain:
    """Single entry point for conversation and action-intent decisions."""

    def __init__(
        self,
        dialogue: DialogueEngine,
        decision_engine: Optional[BozerDecisionEngine] = None,
    ):
        if dialogue is None:
            raise ValueError("dialogue engine is required")
        self.dialogue = dialogue
        if decision_engine is not None:
            self.decisions = decision_engine
        else:
            registry = ToolRegistry.android_defaults()
            reasoner = StructuredMissionReasoner(
                getattr(dialogue, "model_gateway", None),
                registry=registry,
            )
            self.decisions = BozerDecisionEngine(
                registry=registry,
                mission_reasoner=reasoner,
            )

    def respond(
        self,
        message: str,
        *,
        allow_model: bool = False,
        allow_search: bool = False,
        allow_model_reasoning: bool = False,
        granted_permissions: FrozenSet[str] = frozenset(),
        confirmed_step_ids: FrozenSet[str] = frozenset(),
    ) -> BrainResponse:
        decision = self.decisions.decide(
            message,
            granted_permissions=granted_permissions,
            confirmed_step_ids=confirmed_step_ids,
            allow_model_reasoning=allow_model_reasoning,
        )

        if decision.route in {RequestRoute.CHAT, RequestRoute.RESEARCH}:
            result = self.dialogue.respond(
                message, allow_model=allow_model, allow_search=allow_search
            )
            return BrainResponse(result.reply, decision, result)

        if decision.route == RequestRoute.CLARIFICATION:
            reply = (
                "J'ai besoin d'une précision avant de préparer cette demande. "
                "Peux-tu préciser l'action ou les informations manquantes ?"
            )
        elif decision.route == RequestRoute.MULTI_STEP:
            ready = len(decision.task_plan.ready_steps) if decision.task_plan else 0
            total = len(decision.task_plan.steps) if decision.task_plan else 0
            reply = (
                f"J'ai décomposé ta mission en {total} étape(s). "
                f"{ready} étape(s) sont actuellement autorisées. "
                "Aucune action n'a encore été exécutée."
            )
        else:
            ready = len(decision.task_plan.ready_steps) if decision.task_plan else 0
            if ready:
                reply = "J'ai préparé l'action. Elle doit encore être exécutée et vérifiée par Android."
            else:
                reply = "Je ne peux pas encore préparer cette action : une permission ou une confirmation manque."
        return BrainResponse(reply, decision, None)
