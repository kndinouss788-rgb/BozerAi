"""Conservative Mooré <-> French translator backed by a provenance-aware lexicon.

Candidate entries are available by default as provisional lookup results. They are
never mislabeled as reviewed translations. Set allow_candidates=False for a
strict, reviewed-only mode.
"""
from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class TranslationResult:
    source_text: str
    translated_text: str | None
    source_language: str
    target_language: str
    status: str
    confidence: str
    entry_id: str | None = None
    note: str | None = None
    alternatives: tuple[str, ...] = ()
    source: str | None = None
    source_reference: str | None = None
    source_license: str | None = None
    rights_status: str | None = None
    reviewed_by: str | None = None
    reviewed_at: str | None = None

    @property
    def found(self) -> bool:
        return self.translated_text is not None

    @property
    def provisional(self) -> bool:
        return self.status == "candidate"


def normalize_text(value: str) -> str:
    """Normalize spacing and punctuation while preserving Mooré diacritics."""
    value = unicodedata.normalize("NFC", value).casefold().strip()
    value = re.sub(r"^[\W_]+|[\W_]+$", "", value, flags=re.UNICODE)
    return re.sub(r"\\s+", " ", value)


class MooreFrenchTranslator:
    SUPPORTED = {"fr", "mos"}

    def __init__(self, entries: Iterable[dict], *, allow_candidates: bool = True):
        self.allow_candidates = allow_candidates
        self._index: dict[tuple[str, str, str], list[dict]] = {}
        for entry in entries:
            self._validate_entry(entry)
            status = entry["status"]
            if status == "rejected" or (status == "candidate" and not allow_candidates):
                continue
            for source_lang, target_lang, source_key, target_value in (
                ("fr", "mos", "french", "moore"),
                ("mos", "fr", "moore", "french"),
            ):
                key = (source_lang, target_lang, normalize_text(entry[source_key]))
                self._index.setdefault(key, []).append({
                    "target": entry[target_value],
                    "id": entry["id"],
                    "status": status,
                    "note": entry.get("note"),
                    "source": entry.get("source"),
                    "source_reference": entry.get("source_reference"),
                    "source_license": entry.get("source_license"),
                    "rights_status": entry.get("rights_status"),
                    "reviewed_by": entry.get("reviewed_by"),
                    "reviewed_at": entry.get("reviewed_at"),
                })

    @staticmethod
    def _validate_entry(entry: dict) -> None:
        required = ("id", "french", "moore", "status")
        if not isinstance(entry, dict) or any(
            not isinstance(entry.get(key), str) or not entry[key].strip()
            for key in required
        ):
            raise ValueError("each lexicon entry needs non-empty id, french, moore and status")
        if entry["status"] not in {"candidate", "validated", "rejected"}:
            raise ValueError(f"unsupported lexicon status for {entry['id']}")
        if entry["status"] == "validated":
            if not entry.get("source") or not entry.get("reviewed_by"):
                raise ValueError(
                    f"validated entry {entry['id']} needs source and native-speaker reviewer"
                )

    def translate(
        self, text: str, source_language: str, target_language: str
    ) -> TranslationResult:
        source_language = source_language.lower()
        target_language = target_language.lower()
        if source_language not in self.SUPPORTED or target_language not in self.SUPPORTED:
            raise ValueError("supported language codes are fr (French) and mos (Mooré)")
        if source_language == target_language:
            return TranslationResult(
                text, text, source_language, target_language,
                "unchanged", "exact", note="Source and target languages are identical."
            )
        key = (source_language, target_language, normalize_text(text))
        records = self._index.get(key, [])
        if not records:
            return TranslationResult(
                text, None, source_language, target_language,
                "not_found", "none",
                note=(
                    "No exact dictionary entry is available. Try a shorter expression "
                    "or a different spelling; BOZER does not invent a sentence."
                ),
            )
        targets = tuple(dict.fromkeys(record["target"] for record in records))
        if len(targets) > 1:
            return TranslationResult(
                text, None, source_language, target_language,
                "ambiguous", "none",
                note="Several senses or Mooré forms match; select the intended meaning.",
                alternatives=targets,
            )
        # Prefer a reviewed record when several sources support the same target.
        selected = next((r for r in records if r["status"] == "validated"), records[0])
        status = selected["status"]
        note = selected.get("note")
        if status == "candidate":
            note = (
                (note + " ") if note else ""
            ) + "Provisional dictionary result: native-speaker review has not been recorded."
        return TranslationResult(
            source_text=text,
            translated_text=selected["target"],
            source_language=source_language,
            target_language=target_language,
            status=status,
            confidence="reviewed_exact" if status == "validated" else "provisional_exact",
            entry_id=selected["id"],
            note=note,
            source=selected.get("source"),
            source_reference=selected.get("source_reference"),
            source_license=selected.get("source_license"),
            rights_status=selected.get("rights_status"),
            reviewed_by=selected.get("reviewed_by"),
            reviewed_at=selected.get("reviewed_at"),
        )


def load_default_translator(
    *, allow_candidates: bool = True, lexicon_path: str | Path | None = None
) -> MooreFrenchTranslator:
    """Load the full working lexicon; candidate results are visibly provisional.

    Set allow_candidates=False to restrict the translator to reviewed entries.
    """
    if lexicon_path is None:
        lexicon_path = (
            Path(__file__).resolve().parents[2]
            / "data" / "moore_french" / "lexicon.json"
        )
    lexicon_path = Path(lexicon_path)
    payload = json.loads(lexicon_path.read_text(encoding="utf-8"))
    if payload.get("format") != "bozer-moore-french-lexicon-v1":
        raise ValueError("unsupported Mooré/French lexicon format")
    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise ValueError("lexicon entries must be a list")

    if allow_candidates:
        candidates_path = lexicon_path.parent / "malm_candidates.jsonl"
        if candidates_path.is_file():
            with candidates_path.open(encoding="utf-8") as stream:
                for line_number, line in enumerate(stream, start=1):
                    if not line.strip():
                        continue
                    try:
                        row = json.loads(line)
                    except json.JSONDecodeError as exc:
                        raise ValueError(
                            f"invalid MALM candidate JSON at line {line_number}"
                        ) from exc
                    source_ids = row.get("source_ids", [])
                    if isinstance(source_ids, list):
                        source_reference = ", ".join(str(item) for item in source_ids)
                    else:
                        source_reference = str(source_ids) if source_ids else None
                    entries.append({
                        "id": row["id"],
                        "french": row["french"],
                        "moore": row["moore"],
                        "status": "candidate",
                        "source": "MALM Data — progressio-media/malm-data",
                        "source_reference": source_reference,
                        "source_license": (
                            "MIT declared by progressio-media/malm-data; preserve its "
                            "copyright and license notice."
                        ),
                        "rights_status": (
                            "upstream_dataset_MIT_license_declared; source IDs preserved"
                        ),
                        "note": (
                            "Imported MALM dictionary candidate. The source reference is "
                            "retained; spelling, sense and usage have not been natively reviewed."
                        ),
                    })
    return MooreFrenchTranslator(entries, allow_candidates=allow_candidates)
