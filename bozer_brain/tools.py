"""Explicit tool contracts and authorization checks for BOZER.

This module never executes tools. It decides whether an Android-side adapter
may be asked to execute a proposal after validating the contract, permission,
and confirmation state.
"""
from dataclasses import dataclass
from typing import Dict, FrozenSet, Mapping, Optional, Tuple

from .models import ActionProposal


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    required_arguments: FrozenSet[str]
    required_permission: Optional[str] = None
    requires_confirmation: bool = False
    description: str = ""


@dataclass(frozen=True)
class ToolDecision:
    allowed: bool
    tool_name: Optional[str]
    reason: str


class ToolRegistry:
    """In-memory registry of capabilities exposed by trusted app adapters."""

    def __init__(self, definitions: Tuple[ToolDefinition, ...] = ()):
        self._tools: Dict[str, ToolDefinition] = {}
        for definition in definitions:
            self.register(definition)

    def register(self, definition: ToolDefinition) -> None:
        if not definition.name.strip():
            raise ValueError("tool name must not be empty")
        if definition.name in self._tools:
            raise ValueError(f"tool already registered: {definition.name}")
        self._tools[definition.name] = definition

    def get(self, name: str) -> Optional[ToolDefinition]:
        return self._tools.get(name)

    def names(self) -> Tuple[str, ...]:
        return tuple(sorted(self._tools))

    def authorize(
        self,
        proposal: ActionProposal,
        granted_permissions: FrozenSet[str] = frozenset(),
        user_confirmed: bool = False,
    ) -> ToolDecision:
        if not proposal.safe_to_execute:
            return ToolDecision(False, None, "proposal_not_actionable")

        definition = self.get(proposal.action)
        if definition is None:
            return ToolDecision(False, None, "tool_not_registered")

        missing = definition.required_arguments.difference(proposal.arguments)
        if missing:
            return ToolDecision(False, definition.name, "missing_required_arguments")

        permission = definition.required_permission
        if permission and permission not in granted_permissions:
            return ToolDecision(False, definition.name, "permission_not_granted")

        needs_confirmation = (
            definition.requires_confirmation or proposal.requires_confirmation
        )
        if needs_confirmation and not user_confirmed:
            return ToolDecision(False, definition.name, "confirmation_required")

        return ToolDecision(True, definition.name, "authorized_for_adapter")

    @classmethod
    def android_defaults(cls) -> "ToolRegistry":
        """Contracts only; Android adapters must implement the actual operations."""
        return cls((
            ToolDefinition(
                "set_alarm", frozenset({"time"}), "SCHEDULE_EXACT_ALARM",
                description="Request Android to schedule an alarm.",
            ),
            ToolDefinition(
                "open_app", frozenset({"target"}),
                description="Request Android to open an installed application.",
            ),
            ToolDefinition(
                "send_message", frozenset({"target"}),
                "SEND_SMS_OR_MESSAGING_APP", True,
                "Prepare an outgoing message; require explicit confirmation.",
            ),
            ToolDefinition(
                "make_call", frozenset({"target"}), "CALL_PHONE", True,
                "Prepare a phone call; require explicit confirmation.",
            ),
        ))
