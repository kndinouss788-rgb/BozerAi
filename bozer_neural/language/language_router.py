"""Conservative language routing for BOZER.

Mooré detection is deliberately not guessed from short strings: use an explicit
user choice or a future evaluated language-identification model.
"""
from dataclasses import dataclass

@dataclass(frozen=True)
class LanguageDecision:
    language: str
    confidence: float
    method: str

SUPPORTED = {"fr", "en", "mos"}

def choose_language(text: str, *, explicit: str | None = None) -> LanguageDecision:
    if not text.strip():
        raise ValueError("text must not be empty")
    if explicit:
        code = explicit.lower()
        if code not in SUPPORTED:
            raise ValueError(f"unsupported language: {code}")
        return LanguageDecision(code, 1.0, "user_selected")
    # Safe baseline: ASCII-heavy short utterances cannot reliably distinguish
    # French, English and Mooré. Default to French only as the product locale.
    return LanguageDecision("fr", 0.25, "product_default_uncertain")
