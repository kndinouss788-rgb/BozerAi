"""Typed data models shared by BOZER's decision core."""

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, Optional


class IntentKind(str, Enum):
    SET_ALARM = "set_alarm"
    OPEN_APP = "open_app"
    SEND_MESSAGE = "send_message"
    MAKE_CALL = "make_call"
    CHAT = "chat"


@dataclass(frozen=True)
class Intent:
    kind: IntentKind
    raw_text: str
    target: Optional[str] = None
    time_text: Optional[str] = None
    confidence: float = 0.0
    slots: Dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ActionProposal:
    action: str
    arguments: Dict[str, Any]
    requires_confirmation: bool
    required_permission: Optional[str]
    safe_to_execute: bool
    explanation: str
