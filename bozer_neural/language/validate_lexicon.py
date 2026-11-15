"""Validate the BOZER-owned Mooré/French lexicon without inventing translations."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

VALID_STATUSES = {"candidate", "validated", "rejected"}
VALID_CATEGORIES = {"word", "phrase", "expression", "proverb", "sentence"}


def validate_payload(payload: dict) -> list[str]:
    errors: list[str] = []
    if payload.get("format") != "bozer-moore-french-lexicon-v1":
        errors.append("unsupported lexicon format")
    entries = payload.get("entries")
    if not isinstance(entries, list):
        return errors + ["entries must be a list"]

    ids: set[str] = set()
    keys: set[tuple[str, str]] = set()
    for index, entry in enumerate(entries):
        prefix = f"entries[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{prefix} must be an object")
            continue
        for field in ("id", "french", "moore", "status"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append(f"{prefix}.{field} must be a non-empty string")
        if any(not isinstance(entry.get(field), str) or not entry[field].strip()
               for field in ("id", "french", "moore", "status")):
            continue
        if entry["id"] in ids:
            errors.append(f"{prefix}.id is duplicated: {entry['id']}")
        ids.add(entry["id"])
        if entry["status"] not in VALID_STATUSES:
            errors.append(f"{prefix}.status is invalid")
        category = entry.get("category", "phrase")
        if category not in VALID_CATEGORIES:
            errors.append(f"{prefix}.category is invalid")
        for lang, value in (("fr", entry["french"]), ("mos", entry["moore"])):
            key = (lang, value.casefold().strip())
            if key in keys:
                errors.append(f"{prefix} duplicates a {lang} lookup key")
            keys.add(key)
        if entry["status"] == "validated":
            if not entry.get("source") or not entry.get("reviewed_by"):
                errors.append(f"{prefix} validated entry needs source and reviewed_by")
            if not entry.get("source_license"):
                errors.append(f"{prefix} validated entry needs source_license")
            if not entry.get("reviewed_at"):
                errors.append(f"{prefix} validated entry needs reviewed_at")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lexicon", type=Path)
    args = parser.parse_args()
    payload = json.loads(args.lexicon.read_text(encoding="utf-8"))
    errors = validate_payload(payload)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"OK: {len(payload['entries'])} lexicon entries checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
