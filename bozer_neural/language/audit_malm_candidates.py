"""Audit MALM-derived Mooré/French candidates without automatically validating them."""
from __future__ import annotations
import argparse
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

METADATA_GLOSS = re.compile(
    r"(^|\b)(part\.?|pron\.?|adj\.?|adv\.?|interj\.?|confirmé|source|référence|"
    r"ème métalinguistique|ton haut|downstep|morphème|déclaratif|inaccompli)(\b|$)|SRC-\d+",
    re.IGNORECASE,
)

def normal(value: str) -> str:
    return unicodedata.normalize("NFC", value).casefold().strip()

def read_jsonl(path: Path) -> list[dict]:
    rows = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid JSON on line {line_number}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"Line {line_number} must contain a JSON object")
        rows.append(row)
    return rows

def audit_rows(rows: list[dict]) -> tuple[dict, list[dict]]:
    ids: set[str] = set()
    pairs: set[tuple[str, str]] = set()
    by_moore: dict[str, set[str]] = defaultdict(set)
    by_french: dict[str, set[str]] = defaultdict(set)
    source_counts: dict[str, int] = defaultdict(int)
    for row in rows:
        moore = normal(str(row.get("moore", "")))
        french = normal(str(row.get("french", "")))
        by_moore[moore].add(french)
        by_french[french].add(moore)

    queue = []
    invalid = duplicate_ids = duplicate_pairs = 0
    for row in rows:
        identifier = str(row.get("id", "")).strip()
        moore = unicodedata.normalize("NFC", str(row.get("moore", "")).strip())
        french = unicodedata.normalize("NFC", str(row.get("french", "")).strip())
        moore_key, french_key = normal(moore), normal(french)
        flags = []
        if not identifier or not moore or not french:
            invalid += 1
            flags.append("missing_required_field")
        if identifier in ids:
            duplicate_ids += 1
            flags.append("duplicate_id")
        ids.add(identifier)
        pair = (french_key, moore_key)
        if pair in pairs:
            duplicate_pairs += 1
            flags.append("duplicate_exact_pair")
        pairs.add(pair)

        sources = row.get("sources", [])
        if isinstance(sources, list):
            for source in sources:
                source_counts[str(source)] += 1
        if METADATA_GLOSS.search(french):
            flags.append("technical_or_metadata_gloss")
        if moore.startswith(("-", "/")) or "/" in moore:
            flags.append("morpheme_or_symbolic_form")
        if len(by_moore[moore_key]) > 1:
            flags.append("ambiguous_moore_form")
        if len(by_french[french_key]) > 1:
            flags.append("multiple_moore_forms_for_french_gloss")
        if flags:
            queue.append({
                "id": identifier, "moore": moore, "french": french,
                "sources": sources if isinstance(sources, list) else [],
                "source_ids": row.get("source_ids", []),
                "flags": sorted(set(flags)),
            })

    report = {
        "schema": "bozer-malm-quality-audit-v1",
        "entries": len(rows),
        "native_validated_entries": 0,
        "rights_cleared_entries": 0,
        "invalid_rows": invalid,
        "duplicate_ids": duplicate_ids,
        "duplicate_exact_pairs": duplicate_pairs,
        "ambiguous_moore_forms": sum(len(v) > 1 for v in by_moore.values()),
        "rows_using_ambiguous_moore_forms": sum(
            len(by_moore[normal(str(row.get("moore", "")))]) > 1 for row in rows
        ),
        "french_glosses_with_multiple_moore_forms": sum(
            len(v) > 1 for v in by_french.values()
        ),
        "rows_in_multi_form_french_glosses": sum(
            len(by_french[normal(str(row.get("french", "")))]) > 1 for row in rows
        ),
        "review_queue_entries": len(queue),
        "review_flag_counts": {
            flag: sum(flag in item["flags"] for item in queue)
            for flag in sorted({flag for item in queue for flag in item["flags"]})
        },
        "source_counts": dict(sorted(source_counts.items())),
        "policy": {
            "all_imported_entries_remain_candidates": True,
            "automatic_promotion_forbidden": True,
            "diacritics_preserved": True,
            "multiple_forms_never_auto_resolved": True,
        },
    }
    return report, queue

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--review-queue", type=Path, required=True)
    args = parser.parse_args()
    report, queue = audit_rows(read_jsonl(args.input))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.review_queue.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.review_queue.write_text(
        "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in queue),
        encoding="utf-8",
    )
    print(
        f"OK: {report['entries']} candidates; "
        f"{report['review_queue_entries']} require human review; "
        f"{report['ambiguous_moore_forms']} Mooré forms and "
        f"{report['french_glosses_with_multiple_moore_forms']} French glosses are ambiguous."
    )
    return 1 if report["invalid_rows"] or report["duplicate_ids"] or report["duplicate_exact_pairs"] else 0

if __name__ == "__main__":
    raise SystemExit(main())
