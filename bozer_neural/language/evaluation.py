"""Small, deterministic evaluator for reviewed translation benchmark sets."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Iterable
from .moore_french import MooreFrenchTranslator, normalize_text


@dataclass(frozen=True)
class TranslationExample:
    source_text: str
    source_language: str
    target_language: str
    reference: str


def evaluate_translator(
    translator: MooreFrenchTranslator, examples: Iterable[TranslationExample]
) -> dict:
    rows = list(examples)
    if not rows:
        raise ValueError("translation evaluation requires at least one example")
    exact = covered = ambiguous = 0
    details = []
    for item in rows:
        result = translator.translate(
            item.source_text, item.source_language, item.target_language
        )
        found = result.translated_text is not None
        covered += int(found)
        match = bool(found and normalize_text(result.translated_text) == normalize_text(item.reference))
        exact += int(match)
        ambiguous += int(result.status == "ambiguous")
        details.append({
            "source": item.source_text,
            "reference": item.reference,
            "prediction": result.translated_text,
            "status": result.status,
            "exact_match": match,
        })
    total = len(rows)
    return {
        "examples": total,
        "exact_matches": exact,
        "exact_match_accuracy": exact / total,
        "coverage": covered / total,
        "ambiguous_rate": ambiguous / total,
        "details": details,
    }
