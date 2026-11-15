"""Reviewed pronunciation lexicon contract for multilingual BOZER speech."""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
import json
import unicodedata


def _key(value: str) -> str:
    return unicodedata.normalize("NFC", value).casefold().strip()


@dataclass(frozen=True)
class PronunciationEntry:
    spelling: str
    language: str
    phonemes: str
    source: str
    status: str
    reviewed_by: str | None = None
    source_license: str | None = None
    rights_status: str = "pending"

    def __post_init__(self):
        if not all(v.strip() for v in (self.spelling, self.language,
                                       self.phonemes, self.source, self.status)):
            raise ValueError("pronunciation entry fields must be non-empty")
        if self.status not in {"candidate", "validated", "rejected"}:
            raise ValueError("unsupported pronunciation status")
        if self.status == "validated" and not self.reviewed_by:
            raise ValueError("validated pronunciation requires a reviewer")
        if self.status == "validated" and (not self.source_license or self.rights_status != "cleared"):
            raise ValueError("validated pronunciation requires cleared source rights and a license")


class PronunciationLexicon:
    def __init__(self, entries: Iterable[PronunciationEntry], *, allow_candidates=False):
        self._index: dict[tuple[str, str], list[PronunciationEntry]] = {}
        for entry in entries:
            if entry.status == "rejected":
                continue
            if entry.status == "candidate" and not allow_candidates:
                continue
            key = (entry.language, _key(entry.spelling))
            self._index.setdefault(key, []).append(entry)

    def lookup(self, spelling: str, language: str) -> tuple[PronunciationEntry, ...]:
        return tuple(self._index.get((language, _key(spelling)), ()))


def load_pronunciation_lexicon(path: str | Path | None = None, *, allow_candidates=False):
    if path is None:
        path = Path(__file__).resolve().parents[2] / "data" / "moore_french" / "pronunciation_lexicon.json"
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if payload.get("format") != "bozer-pronunciation-lexicon-v1":
        raise ValueError("unsupported pronunciation lexicon format")
    rows = payload.get("entries")
    if not isinstance(rows, list):
        raise ValueError("pronunciation entries must be a list")
    return PronunciationLexicon(
        [PronunciationEntry(**row) for row in rows],
        allow_candidates=allow_candidates,
    )
