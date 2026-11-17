"""Indexed, provenance-aware lookup for the BOZER Mooré–French lexicon."""
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
import json
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
    source: str | None = None
    source_license: str | None = None
    rights_status: str | None = None
    source_type: str | None = None
    native_review_status: str | None = None
    note: str | None = None
    reviewed_by: str | None = None
    reviewed_at: str | None = None

    @property
    def provisional(self) -> bool:
        return self.status == "candidate"


class MooreFrenchDictionary:
    """Exact and substring lookup; candidate records stay visibly provisional."""

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
            source=entry.get("source"),
            source_license=entry.get("source_license"),
            rights_status=entry.get("rights_status"),
            source_type=entry.get("source_type"),
            native_review_status=entry.get("native_review_status"),
            note=entry.get("note"),
            reviewed_by=entry.get("reviewed_by"),
            reviewed_at=entry.get("reviewed_at"),
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


def load_default_dictionary(*, include_candidates: bool = True,
                            lexicon_path: str | Path | None = None) -> MooreFrenchDictionary:
    """Load the working dictionary; set include_candidates=False for strict lookup."""
    if lexicon_path is None:
        lexicon_path = Path(__file__).resolve().parents[2] / "data" / "moore_french" / "lexicon.json"
    path = Path(lexicon_path)
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("format") != "bozer-moore-french-lexicon-v1":
        raise ValueError("unsupported Mooré/French lexicon format")
    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise ValueError("lexicon entries must be a list")
    if include_candidates:
        candidates_path = path.parent / "malm_candidates.jsonl"
        if candidates_path.is_file():
            with candidates_path.open(encoding="utf-8") as stream:
                for line_number, line in enumerate(stream, 1):
                    if not line.strip():
                        continue
                    try:
                        row = json.loads(line)
                    except json.JSONDecodeError as exc:
                        raise ValueError(f"invalid candidate JSON at line {line_number}") from exc
                    source_refs = []
                    for field in ("sources", "source_ids"):
                        values = row.get(field, [])
                        if isinstance(values, list):
                            source_refs.extend(str(item) for item in values if item)
                        elif values:
                            source_refs.append(str(values))
                    source_reference = row.get("source_reference") or ", ".join(dict.fromkeys(source_refs))
                    entries.append({
                        "id": row["id"], "french": row["french"],
                        "moore": row["moore"], "status": "candidate",
                        "source": "MALM Data — progressio-media/malm-data",
                        "source_reference": source_reference,
                        "source_license": (
                            "MIT declared by progressio-media/malm-data; preserve its "
                            "copyright and license notice."
                        ),
                        "rights_status": row.get(
                            "rights_status", "upstream_dataset_MIT_license_declared"
                        ),
                        "source_type": row.get("source_type"),
                        "native_review_status": row.get("native_review_status", "pending"),
                        "category": row.get("category", "word"),
                        "note": (
                            "Imported MALM dictionary candidate. The source reference is "
                            "retained; spelling, sense and usage have not been natively reviewed."
                        ),
                    })
    return MooreFrenchDictionary(entries, include_candidates=include_candidates)
