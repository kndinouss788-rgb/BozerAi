"""Validate categorized JSONL corpus and disjoint prompt splits."""
import argparse
import json
from collections import Counter
from pathlib import Path

CATEGORIES = {
    "dialogue_naturel", "comprehension_demande", "raisonnement_resolution",
    "planification_missions", "utilisation_outils_verification",
    "securite_confidentialite_honnetete",
}


def read_records(path):
    records = []
    for line_number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        try:
            item = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{line_number}: invalid JSON: {exc}") from exc
        if not isinstance(item, dict):
            raise ValueError(f"{path}:{line_number}: expected object")
        for key in ("category", "user", "assistant"):
            if not isinstance(item.get(key), str) or not item[key].strip():
                raise ValueError(f"{path}:{line_number}: missing/invalid {key}")
        if item["category"] not in CATEGORIES:
            raise ValueError(f"{path}:{line_number}: unknown category {item['category']}")
        records.append(item)
    if not records:
        raise ValueError(f"{path}: no records")
    return records


def validate_splits(train_path, validation_path, test_path):
    splits = {
        "train": read_records(train_path),
        "validation": read_records(validation_path),
        "test": read_records(test_path),
    }
    prompts = {}
    report = {}
    for name, records in splits.items():
        counts = Counter(x["category"] for x in records)
        missing = CATEGORIES - set(counts)
        if missing:
            raise ValueError(f"{name} is missing categories: {sorted(missing)}")
        report[name] = dict(counts)
        for item in records:
            key = item["user"].strip().casefold()
            if key in prompts:
                raise ValueError(f"duplicate prompt across splits: {item['user']}")
            prompts[key] = name
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", default="data/bozer_neural_v0_3/train_fr.jsonl")
    parser.add_argument("--validation", default="data/bozer_neural_v0_3/validation_fr.jsonl")
    parser.add_argument("--test", default="data/bozer_neural_v0_3/test_fr.jsonl")
    args = parser.parse_args()
    result = validate_splits(args.train, args.validation, args.test)
    for split, counts in result.items():
        print(f"{split}: {sum(counts.values())} exemples")
        for category in sorted(counts):
            print(f"  {category}: {counts[category]}")


if __name__ == "__main__":
    main()
