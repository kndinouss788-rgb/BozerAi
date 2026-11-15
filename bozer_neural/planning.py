"""Typed, permission-neutral planning contracts for BOZER NEURAL.

Plans are proposals only. Android remains responsible for permission checks,
user confirmation, execution and verification.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

class ActionKind(str, Enum):
    ANSWER = "answer"
    ADD_TASK = "add_task"
    LIST_TASKS = "list_tasks"
    SET_ALARM = "set_alarm"
    OPEN_APP = "open_app"
    CALL = "call"
    SEND_MESSAGE = "send_message"
    SEARCH_PUBLIC = "search_public"
    TRANSLATE = "translate"

@dataclass(frozen=True)
class PlannedAction:
    kind: ActionKind
    arguments: dict[str, Any] = field(default_factory=dict)
    requires_confirmation: bool = False
    required_capability: str | None = None

@dataclass(frozen=True)
class ActionPlan:
    intent: str
    actions: tuple[PlannedAction, ...]
    explanation: str
    confidence: float

    def __post_init__(self):
        if not self.intent.strip() or not self.explanation.strip():
            raise ValueError("intent and explanation are required")
        if not 0 <= self.confidence <= 1:
            raise ValueError("confidence must be between 0 and 1")
        for action in self.actions:
            if action.kind in {ActionKind.CALL, ActionKind.SEND_MESSAGE,
                               ActionKind.SET_ALARM, ActionKind.OPEN_APP}:
                if not action.required_capability:
                    raise ValueError(f"{action.kind.value} requires a capability gate")
