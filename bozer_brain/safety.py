"""Central safety policy for proposed device actions."""

from .models import Intent, IntentKind


SENSITIVE_INTENTS = {IntentKind.SEND_MESSAGE, IntentKind.MAKE_CALL}


def requires_confirmation(intent: Intent) -> bool:
    """Require explicit user confirmation before external communication."""
    return intent.kind in SENSITIVE_INTENTS


def is_actionable(intent: Intent) -> bool:
    """Reject missing targets and unsupported/ambiguous requests."""
    if intent.kind == IntentKind.SET_ALARM:
        return bool(intent.time_text)
    if intent.kind in {IntentKind.OPEN_APP, IntentKind.SEND_MESSAGE, IntentKind.MAKE_CALL}:
        return bool(intent.target)
    return False
