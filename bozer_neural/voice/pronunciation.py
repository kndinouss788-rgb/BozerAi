"""Text preparation hooks for multilingual speech; never strips Mooré marks."""
from __future__ import annotations
import unicodedata
import re

def normalize_for_speech(text: str, language: str) -> str:
    if not text.strip():
        return ""
    # NFC preserves combining marks in canonical form; do not ASCII-fold Mooré.
    value = unicodedata.normalize("NFC", text)
    value = re.sub(r"\s+", " ", value).strip()
    if language not in {"fr-FR", "en-US", "mos-BF"}:
        raise ValueError("unsupported speech language")
    return value
