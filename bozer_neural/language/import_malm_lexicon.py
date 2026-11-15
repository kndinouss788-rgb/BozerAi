"""Convert a MALM-format Mooré lexicon export to BOZER candidate entries.

This tool does not fetch or redistribute third-party data. The operator must
provide a locally obtained file and confirm commercial training and
redistribution rights. Imported entries remain candidates until native review.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

def convert(payload: dict) -> dict:
    rows = payload.get("entrées")
    if not isinstance(rows, list):
        raise ValueError("expected MALM JSON with an entrées list")
    entries = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        moore, french = row.get("mooré"), row.get("français")
        if not isinstance(moore, str) or not isinstance(french, str):
            continue
        moore, french = moore.strip(), french.strip()
        if not moore or not french:
            continue
        entries.append({
            "id": f"malm-{row.get('id', len(entries) + 1)}",
            "french": french, "moore": moore, "status": "candidate",
            "source": f"MALM:{row.get('source', 'unspecified')}",
            "reviewed_by": None,
            "note": "Imported candidate; native-speaker review required.",
        })
    return {
        "format": "bozer-moore-french-lexicon-v1",
        "languages": {"fr": "Français", "mos": "Mooré"},
        "policy": {"default_acceptance": "validated_only",
                   "candidate_entries_are_not_translation_authority": True,
                   "require_native_speaker_review": True,
                   "do_not_invent_missing_translations": True},
        "entries": entries,
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--commercial-rights-confirmed", action="store_true",
                        help="Confirm commercial training and redistribution rights.")
    args = parser.parse_args()
    if not args.commercial_rights_confirmed:
        parser.error("refusing import: commercial rights confirmation is required")
    payload = json.loads(args.input.read_text(encoding="utf-8"))
    converted = convert(payload)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(converted, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(converted['entries'])} candidate entries; none are validated.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())