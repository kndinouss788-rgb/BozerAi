"""Prepare source-isolated, rights-cleared multilingual parallel text for training."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path

SUPPORTED_LANGUAGES = {"fr", "en", "mos"}
FORMAT = "bozer-parallel-corpus-v1"


def validate_parallel_records(records: list[dict]) -> list[str]:
    errors = []
    seen_ids = set()
    for index, row in enumerate(records):
        prefix = f"records[{index}]"
        if not isinstance(row, dict):
            errors.append(f"{prefix} must be an object")
            continue
        entry_id = row.get("id")
        if not isinstance(entry_id, str) or not entry_id.strip():
            errors.append(f"{prefix}.id is missing")
        elif entry_id in seen_ids:
            errors.append(f"{prefix}.id is duplicated")
        else:
            seen_ids.add(entry_id)
        for field in ("source_id", "source_text", "target_text", "source",
                      "source_license", "reviewed_by"):
            if not isinstance(row.get(field), str) or not row[field].strip():
                errors.append(f"{prefix}.{field} is missing")
        if row.get("source_language") not in SUPPORTED_LANGUAGES:
            errors.append(f"{prefix}.source_language is unsupported")
        if row.get("target_language") not in SUPPORTED_LANGUAGES:
            errors.append(f"{prefix}.target_language is unsupported")
        if row.get("source_language") == row.get("target_language"):
            errors.append(f"{prefix} source and target languages must differ")
        if row.get("status") != "validated":
            errors.append(f"{prefix} is not linguistically validated")
        if row.get("rights_status") != "cleared":
            errors.append(f"{prefix} source rights are not cleared")
    return errors


def _split_for_source(source_id: str, train_percent: int, validation_percent: int) -> str:
    bucket = int(hashlib.sha256(source_id.encode("utf-8")).hexdigest()[:8], 16) % 100
    if bucket < train_percent:
        return "train"
    if bucket < train_percent + validation_percent:
        return "validation"
    return "test"


def prepare_parallel_corpus(input_path, output_dir, *, train_percent=90,
                            validation_percent=5):
    input_path = Path(input_path)
    output_dir = Path(output_dir)
    if train_percent < 1 or validation_percent < 1 or train_percent + validation_percent >= 100:
        raise ValueError("split percentages must be positive and leave room for test")
    records = []
    for line_number, line in enumerate(input_path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSONL at line {line_number}") from exc
    errors = validate_parallel_records(records)
    if errors:
        raise ValueError("parallel corpus validation failed: " + "; ".join(errors[:12]))

    source_assignments = {}
    for row in records:
        source_id = row["source_id"]
        split = _split_for_source(source_id, train_percent, validation_percent)
        prior = source_assignments.setdefault(source_id, split)
        if prior != split:
            raise RuntimeError("source-isolated split assignment changed unexpectedly")

    partitions = {"train": [], "validation": [], "test": []}
    for row in records:
        split = source_assignments[row["source_id"]]
        source_language = row["source_language"]
        target_language = row["target_language"]
        partitions[split].append({
            "category": f"translation_{source_language}_{target_language}",
            "user": f"Translate from {source_language} to {target_language}: {row['source_text']}",
            "assistant": row["target_text"],
            "history": [],
            "source_id": row["source_id"],
            "language_pair": [source_language, target_language],
            "entry_id": row["id"],
        })
    if any(not partition for partition in partitions.values()):
        counts = {name: len(items) for name, items in partitions.items()}
        raise ValueError(
            "not enough independent source documents to fill all partitions; "
            f"current counts={counts}"
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    language_pairs = {}
    for row in records:
        key = f"{row['source_language']}-{row['target_language']}"
        language_pairs[key] = language_pairs.get(key, 0) + 1
    for name, items in partitions.items():
        items.sort(key=lambda item: item["entry_id"])
        path = output_dir / f"{name}.jsonl"
        path.write_text(
            "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in items),
            encoding="utf-8",
        )
    report = {
        "format": FORMAT,
        "records": len(records),
        "source_documents": len(source_assignments),
        "records_by_split": {name: len(items) for name, items in partitions.items()},
        "source_documents_by_split": {
            name: len({item["source_id"] for item in items})
            for name, items in partitions.items()
        },
        "language_pairs": language_pairs,
        "rights_gate": "all records declared rights-cleared and reviewed",
        "rights_independently_verified": False,
        "source_isolation": True,
    }
    (output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report
