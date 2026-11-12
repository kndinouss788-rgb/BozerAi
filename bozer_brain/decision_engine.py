"""Decision pipeline for BOZER: classify, plan, gate, and report.

This is a pure orchestration layer. It never invokes a model, network connector,
Android API, or action adapter. The caller remains responsible for permissions,
user confirmation, execution, and passing back observed outcomes.
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

    def __init__(self, registry: Optional[ToolRegistry] = None):
        self.registry = registry or ToolRegistry.android_defaults()
        self.task_planner = TaskPlanner(self.registry)

    def decide(
        self,
        message: str,
        granted_permissions: FrozenSet[str] = frozenset(),
        confirmed_step_ids: FrozenSet[str] = frozenset(),
    ) -> BrainDecision:
        analysis = analyze_request(message)

        if analysis.route == RequestRoute.MULTI_STEP:
            parts = [part.strip(" ,;.") for part in _SPLIT_ACTIONS.split(message) if part.strip(" ,;.")]
            # A mission is accepted only when every segment is independently understood.
            parsed = [parse_intent(part) for part in parts]
            proposals = [plan_action(intent) for intent in parsed]
            if len(parts) < 2 or any(not proposal.safe_to_execute for proposal in proposals):
                return BrainDecision(
                    RequestRoute.CLARIFICATION, analysis.confidence,
                    "La mission contient des étapes que BOZER ne sait pas encore décomposer sans ambiguïté.",
                    analysis.action_proposal, None, True,
                    ("mission_decomposition_incomplete", "nothing_was_executed"),
                )
            steps = tuple(
                TaskStep(
                    step_id=f"step-{index + 1}",
                    proposal=proposal,
                    depends_on=(f"step-{index}",) if index > 1 else (),
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
                ("dependency_ordered_plan_created", "planning_only_no_execution"),
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
