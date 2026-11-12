"""Decision pipeline for BOZER: classify, plan, gate, and report.

This is a pure orchestration layer. It never invokes Android APIs or executes an
action. Optional model reasoning may propose mission steps, but each proposal is
re-parsed and authorized by the deterministic local registry.
"""
from dataclasses import dataclass
import re
from typing import FrozenSet, Optional, Tuple

from .models import ActionProposal
from .request_router import RequestRoute, analyze_request
from .task_planner import TaskPlan, TaskPlanner, TaskStep
from .intent import parse_intent
from .planner import plan_action
from .tools import ToolRegistry
from .mission_reasoner import (
    MissionReasoningStatus,
    StructuredMissionReasoner,
)


_SPLIT_ACTIONS = re.compile(
    r"\s*(?:,?\s+(?:puis|ensuite|et enfin)\s+|\s*;\s*)",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class BrainDecision:
    route: RequestRoute
    confidence: float
    summary: str
    action_proposal: ActionProposal
    task_plan: Optional[TaskPlan]
    requires_user_input: bool
    reasons: Tuple[str, ...]


class BozerDecisionEngine:
    """Create safe, inspectable decisions from natural-language requests."""

    def __init__(
        self,
        registry: Optional[ToolRegistry] = None,
        mission_reasoner: Optional[StructuredMissionReasoner] = None,
    ):
        self.registry = registry or ToolRegistry.android_defaults()
        self.task_planner = TaskPlanner(self.registry)
        self.mission_reasoner = mission_reasoner

    def decide(
        self,
        message: str,
        granted_permissions: FrozenSet[str] = frozenset(),
        confirmed_step_ids: FrozenSet[str] = frozenset(),
        *,
        allow_model_reasoning: bool = False,
    ) -> BrainDecision:
        analysis = analyze_request(message)

        if analysis.route == RequestRoute.MULTI_STEP:
            parts = [
                part.strip(" ,;.")
                for part in _SPLIT_ACTIONS.split(message)
                if part.strip(" ,;.")
            ]
            # Prefer fast deterministic parsing. Ask the optional model only
            # when the request cannot be decomposed without guessing.
            parsed = [parse_intent(part) for part in parts]
            proposals = [plan_action(intent) for intent in parsed]
            deterministic_plan_is_safe = (
                len(parts) >= 2
                and all(proposal.safe_to_execute for proposal in proposals)
                and all(
                    self.registry.get(proposal.action) is not None
                    for proposal in proposals
                )
            )
            if deterministic_plan_is_safe:
                steps = tuple(
                    TaskStep(
                        step_id=f"step-{index}",
                        proposal=proposal,
                        depends_on=(f"step-{index - 1}",) if index > 1 else (),
                    )
                    for index, proposal in enumerate(proposals, start=1)
                )
                plan = self.task_planner.build_plan(
                    steps, granted_permissions, confirmed_step_ids
                )
                return BrainDecision(
                    RequestRoute.MULTI_STEP, analysis.confidence,
                    "Mission décomposée en étapes ordonnées ; chaque étape doit être autorisée avant exécution.",
                    analysis.action_proposal, plan, False,
                    ("deterministic_dependency_ordered_plan", "planning_only_no_execution"),
                )

            if self.mission_reasoner is not None and allow_model_reasoning:
                reasoned = self.mission_reasoner.decompose(
                    message,
                    enabled=True,
                    granted_permissions=granted_permissions,
                    confirmed_step_ids=confirmed_step_ids,
                )
                if reasoned.status == MissionReasoningStatus.PLANNED and reasoned.plan is not None:
                    return BrainDecision(
                        RequestRoute.MULTI_STEP, analysis.confidence,
                        "Le modèle a proposé des étapes, puis BOZER les a validées par ses règles locales. Aucune action n'a été exécutée.",
                        analysis.action_proposal, reasoned.plan, False,
                        ("model_proposed_steps_locally_validated", "authorization_gates_applied",
                         "planning_only_no_execution"),
                    )

                return BrainDecision(
                    RequestRoute.CLARIFICATION, analysis.confidence,
                    "Je n'ai pas pu transformer cette mission en étapes suffisamment sûres. Peux-tu la préciser ?",
                    analysis.action_proposal, None, True,
                    ("model_plan_rejected_or_unavailable", reasoned.reason, "nothing_was_executed"),
                )

            return BrainDecision(
                RequestRoute.CLARIFICATION, analysis.confidence,
                "La mission contient des étapes que BOZER ne sait pas encore décomposer sans ambiguïté.",
                analysis.action_proposal, None, True,
                ("mission_decomposition_incomplete", "nothing_was_executed"),
            )

        if analysis.route == RequestRoute.DEVICE_ACTION:
            plan = self.task_planner.plan_message(
                message, granted_permissions, confirmed_step_ids
            )
            return BrainDecision(
                analysis.route, analysis.confidence, analysis.summary,
                analysis.action_proposal, plan, False,
                analysis.reasons + ("authorization_gate_applied", "planning_only_no_execution"),
            )

        return BrainDecision(
            analysis.route, analysis.confidence, analysis.summary,
            analysis.action_proposal, None, analysis.requires_user_input,
            analysis.reasons,
        )
