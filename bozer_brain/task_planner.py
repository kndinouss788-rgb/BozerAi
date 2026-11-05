"""Dependency-aware task planning with explicit authorization gates.

Plans are data only. This module never executes a phone action or invokes a tool.
"""
from dataclasses import dataclass
from typing import FrozenSet, Iterable, List, Optional, Tuple

from .intent import parse_intent
from .models import ActionProposal
from .planner import plan_action
from .tools import ToolRegistry


@dataclass(frozen=True)
class TaskStep:
    step_id: str
    proposal: ActionProposal
    depends_on: Tuple[str, ...] = ()


@dataclass(frozen=True)
class PlannedStep:
    step_id: str
    proposal: ActionProposal
    depends_on: Tuple[str, ...]
    status: str
    reason: str


@dataclass(frozen=True)
class TaskPlan:
    steps: Tuple[PlannedStep, ...]
    valid: bool
    errors: Tuple[str, ...]

    @property
    def ready_steps(self) -> Tuple[PlannedStep, ...]:
        return tuple(step for step in self.steps if step.status == "ready")

    @property
    def blocked_steps(self) -> Tuple[PlannedStep, ...]:
        return tuple(step for step in self.steps if step.status != "ready")


class TaskPlanner:
    """Validate task graphs, order dependencies, then check tool authorization."""

    def __init__(self, registry: ToolRegistry):
        self.registry = registry

    def plan_message(
        self,
        message: str,
        granted_permissions: FrozenSet[str] = frozenset(),
        confirmed_step_ids: FrozenSet[str] = frozenset(),
    ) -> TaskPlan:
        intent = parse_intent(message)
        proposal = plan_action(intent)
        step = TaskStep("step-1", proposal)
        return self.build_plan((step,), granted_permissions, confirmed_step_ids)

    def build_plan(
        self,
        steps: Iterable[TaskStep],
        granted_permissions: FrozenSet[str] = frozenset(),
        confirmed_step_ids: FrozenSet[str] = frozenset(),
    ) -> TaskPlan:
        items = tuple(steps)
        errors: List[str] = []
        by_id = {}
        for step in items:
            if not step.step_id or not step.step_id.strip():
                errors.append("empty_step_id")
            elif step.step_id in by_id:
                errors.append(f"duplicate_step_id:{step.step_id}")
            else:
                by_id[step.step_id] = step

        for step in items:
            for dependency in step.depends_on:
                if dependency not in by_id:
                    errors.append(f"unknown_dependency:{step.step_id}:{dependency}")
                if dependency == step.step_id:
                    errors.append(f"self_dependency:{step.step_id}")

        if errors:
            return TaskPlan((), False, tuple(errors))

        ordered: List[TaskStep] = []
        pending = list(items)
        completed = set()
        while pending:
            available = next(
                (step for step in pending if set(step.depends_on).issubset(completed)),
                None,
            )
            if available is None:
                return TaskPlan((), False, ("dependency_cycle",))
            ordered.append(available)
            completed.add(available.step_id)
            pending.remove(available)

        results = {}
        for step in ordered:
            failed_dependencies = [
                dep for dep in step.depends_on
                if results[dep].status != "ready"
            ]
            if failed_dependencies:
                result = PlannedStep(
                    step.step_id, step.proposal, step.depends_on,
                    "blocked", "dependency_not_ready:" + ",".join(failed_dependencies),
                )
            else:
                decision = self.registry.authorize(
                    step.proposal,
                    granted_permissions=granted_permissions,
                    user_confirmed=step.step_id in confirmed_step_ids,
                )
                status = "ready" if decision.allowed else "blocked"
                result = PlannedStep(
                    step.step_id, step.proposal, step.depends_on, status, decision.reason
                )
            results[step.step_id] = result

        return TaskPlan(tuple(results[s.step_id] for s in ordered), True, ())


def single_step(message: str) -> TaskStep:
    """Convenience adapter from one explicit natural-language request."""
    intent = parse_intent(message)
    return TaskStep("step-1", plan_action(intent))
