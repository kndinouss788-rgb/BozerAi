"""Rights and review gates for promoting Mooré/French lexicon candidates."""
from __future__ import annotations
from dataclasses import dataclass
from datetime import date
from typing import Iterable


@dataclass(frozen=True)
class ReviewDecision:
    entry_id: str
    approved: bool
    reviewer: str
    reviewed_at: str
    rights_status: str
    note: str = ""


def promote_candidate(entry: dict, decision: ReviewDecision) -> dict:
    """Return a validated copy only when rights and native linguistic review pass."""
    if not isinstance(entry, dict) or entry.get("status") != "candidate":
        raise ValueError("only candidate entries can be promoted")
    if decision.entry_id != entry.get("id"):
        raise ValueError("review decision entry id does not match candidate")
    if not decision.approved:
        raise ValueError("review decision did not approve the candidate")
    if decision.rights_status != "cleared":
        raise ValueError("source rights must be cleared before promotion")
    if not decision.reviewer.strip():
        raise ValueError("native-speaker reviewer is required")
    try:
        date.fromisoformat(decision.reviewed_at)
    except ValueError as exc:
        raise ValueError("reviewed_at must be an ISO date") from exc
    for field in ("source", "source_reference", "source_license"):
        if not isinstance(entry.get(field), str) or not entry[field].strip():
            raise ValueError(f"candidate is missing {field}")
    promoted = dict(entry)
    promoted.update({
        "status": "validated",
        "reviewed_by": decision.reviewer.strip(),
        "reviewed_at": decision.reviewed_at,
        "rights_status": "cleared",
    })
    return promoted


def validate_release_entries(entries: Iterable[dict]) -> list[str]:
    """Return all release blockers; candidates can never enter a public release."""
    errors = []
    seen = set()
    for index, entry in enumerate(entries):
        prefix = f"entries[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{prefix} must be an object")
            continue
        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or not entry_id.strip():
            errors.append(f"{prefix}.id is missing")
            continue
        if entry_id in seen:
            errors.append(f"{prefix}.id is duplicated")
        seen.add(entry_id)
        if entry.get("status") != "validated":
            errors.append(f"{prefix} is not validated")
        if entry.get("rights_status") != "cleared":
            errors.append(f"{prefix} source rights are not cleared")
        for field in ("source", "source_reference", "source_license",
                      "reviewed_by", "reviewed_at"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append(f"{prefix}.{field} is missing")
    return errors
