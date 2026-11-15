"""Reviewed pronunciation lexicon contract for multilingual BOZER speech."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Iterable
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

    def __post_init__(self):
        if not all(v.strip() for v in (self.spelling, self.language,
                                       self.phonemes, self.source, self.status)):
            raise ValueError("pronunciation entry fields must be non-empty")
        if self.status not in {"candidate", "validated", "rejected"}:
            raise ValueError("unsupported pronunciation status")
        if self.status == "validated" and not self.reviewed_by:
            raise ValueError("validated pronunciation requires a reviewer")


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
