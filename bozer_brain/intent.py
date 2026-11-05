"""Small deterministic intent parser; deliberately fails closed."""

import re
from .models import Intent, IntentKind


_ALARM = re.compile(
    r"(?:mets?|r[eé]gle|programme|cr[eé]e)\s+(?:une?\s+)?alarme\s+(?:[àa]\s*)?(\d{1,2}(?::\d{2})?)",
    re.IGNORECASE,
)
_OPEN = re.compile(
    r"(?:ouvre|lance|open)\s+(?:l'application\s+)?([\wÀ-ÿ .'-]{2,40})",
    re.IGNORECASE,
)
_MESSAGE = re.compile(
    r"(?:envoie|envoi|envoie-moi|send)\s+(?:un\s+)?message\s+(?:[àa]\s+)?(.+)",
    re.IGNORECASE,
)
_CALL = re.compile(
    r"(?:appelle|t[eé]l[eé]phone|call)\s+(.+)",
    re.IGNORECASE,
)


def parse_intent(text: str) -> Intent:
    """Extract only simple, explicit intents; otherwise return CHAT."""
    raw = (text or "").strip()
    if not raw:
        return Intent(IntentKind.CHAT, raw, confidence=1.0)

    match = _ALARM.search(raw)
    if match:
        return Intent(IntentKind.SET_ALARM, raw, time_text=match.group(1), confidence=0.96)

    match = _OPEN.search(raw)
    if match:
        target = match.group(1).strip(" .!?")
        if target:
            return Intent(IntentKind.OPEN_APP, raw, target=target, confidence=0.88)

    match = _MESSAGE.search(raw)
    if match:
        return Intent(IntentKind.SEND_MESSAGE, raw, target=match.group(1).strip(), confidence=0.82)

    match = _CALL.search(raw)
    if match:
        return Intent(IntentKind.MAKE_CALL, raw, target=match.group(1).strip(), confidence=0.82)

    return Intent(IntentKind.CHAT, raw, confidence=0.55)
