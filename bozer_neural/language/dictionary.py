"""Indexed, provenance-aware lookup for the BOZER Mooré–French lexicon."""
from __future__ import annotations
from dataclasses import dataclass
from typing import Iterable
from .moore_french import normalize_text


@dataclass(frozen=True)
class DictionaryHit:
    entry_id: str
    headword: str
    translation: str
    language: str
    status: str
    category: str
    part_of_speech: str | None = None
    region: str | None = None
    pronunciation: str | None = None
    tone_notes: str | None = None
    source_reference: str | None = None


class MooreFrenchDictionary:
    """Exact and substring lookup; candidate entries are opt-in, never defaults."""

    def __init__(self, entries: Iterable[dict], *, include_candidates: bool = False):
        self._entries: list[dict] = []
        seen_ids: set[str] = set()
        for entry in entries:
            if not isinstance(entry, dict):
                raise ValueError("dictionary entries must be objects")
            required = ("id", "french", "moore", "status")
            if any(not isinstance(entry.get(k), str) or not entry[k].strip() for k in required):
                raise ValueError("each entry needs id, french, moore and status")
            if entry["id"] in seen_ids:
                raise ValueError(f"duplicate dictionary entry id: {entry['id']}")
            seen_ids.add(entry["id"])
            if entry["status"] not in {"candidate", "validated", "rejected"}:
                raise ValueError(f"invalid entry status: {entry['status']}")
            if entry["status"] == "rejected":
                continue
            if entry["status"] == "candidate" and not include_candidates:
                continue
            if entry["status"] == "validated" and (
                not entry.get("source") or not entry.get("reviewed_by")
            ):
                raise ValueError(f"validated entry {entry['id']} lacks review provenance")
            self._entries.append(dict(entry))

    def _hit(self, entry: dict, language: str) -> DictionaryHit:
        if language == "fr":
            headword, translation = entry["french"], entry["moore"]
        elif language == "mos":
            headword, translation = entry["moore"], entry["french"]
        else:
            raise ValueError("language must be 'fr' or 'mos'")
        return DictionaryHit(
            entry_id=entry["id"],
            headword=headword,
            translation=translation,
            language=language,
            status=entry["status"],
            category=entry.get("category", "phrase"),
            part_of_speech=entry.get("part_of_speech"),
            region=entry.get("region"),
            pronunciation=entry.get("pronunciation"),
            tone_notes=entry.get("tone_notes"),
            source_reference=entry.get("source_reference") or entry.get("source"),
        )

    def exact(self, text: str, language: str) -> tuple[DictionaryHit, ...]:
        key = normalize_text(text)
        if not key:
            return ()
        field = "french" if language == "fr" else "moore" if language == "mos" else None
        if field is None:
            raise ValueError("language must be 'fr' or 'mos'")
        return tuple(self._hit(e, language) for e in self._entries
                     if normalize_text(e[field]) == key)

    def search(self, query: str, language: str, *, category: str | None = None,
               part_of_speech: str | None = None, limit: int = 20) -> tuple[DictionaryHit, ...]:
        if limit < 1:
            raise ValueError("limit must be positive")
        key = normalize_text(query)
        if not key:
            return ()
        field = "french" if language == "fr" else "moore" if language == "mos" else None
        if field is None:
            raise ValueError("language must be 'fr' or 'mos'")
        hits = []
        for entry in self._entries:
            if category and entry.get("category", "phrase") != category:
                continue
            if part_of_speech and entry.get("part_of_speech") != part_of_speech:
                continue
            if key in normalize_text(entry[field]):
                hits.append(self._hit(entry, language))
                if len(hits) >= limit:
                    break
        return tuple(hits)
