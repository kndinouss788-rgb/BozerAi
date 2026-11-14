"""Validate categorized JSONL data, duplicates and optional scenario leakage."""
import argparse
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path

CATEGORIES = {
    "dialogue_naturel", "comprehension_demande", "raisonnement_resolution",
    "planification_missions", "utilisation_outils_verification",
    "securite_confidentialite_honnetete",
}


def normalize_prompt(value):
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return re.sub(r"\s+", " ", normalized).strip()


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
        history = item.get("history", [])
        if not isinstance(history, list):
            raise ValueError(f"{path}:{line_number}: history must be a list")
        for turn_number, turn in enumerate(history, 1):
            if not isinstance(turn, dict):
                raise ValueError(
                    f"{path}:{line_number}: history turn {turn_number} must be an object"
                )
            for key in ("user", "assistant"):
                if not isinstance(turn.get(key), str) or not turn[key].strip():
                    raise ValueError(
                        f"{path}:{line_number}: history turn {turn_number} needs non-empty {key}"
                    )
        scenario_id = item.get("scenario_id")
        if scenario_id is not None and (
            not isinstance(scenario_id, str) or not scenario_id.strip()
        ):
            raise ValueError(f"{path}:{line_number}: scenario_id must be a non-empty string")
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
    prompt_owner = {}
    scenario_owner = {}
    report = {}
    for name, records in splits.items():
        counts = Counter(item["category"] for item in records)
        missing = CATEGORIES - set(counts)
        if missing:
            raise ValueError(f"{name} is missing categories: {sorted(missing)}")
        report[name] = dict(counts)
        for item in records:
            key = normalize_prompt(item["user"])
            if key in prompt_owner:
                raise ValueError(
                    f"duplicate normalized prompt in {prompt_owner[key]} and {name}: "
                    f"{item['user']}"
                )
            prompt_owner[key] = name
            scenario_id = item.get("scenario_id")
            if scenario_id:
                if scenario_id in scenario_owner:
                    raise ValueError(
                        f"scenario_id {scenario_id!r} leaks between "
                        f"{scenario_owner[scenario_id]} and {name}"
                    )
                scenario_owner[scenario_id] = name
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", default="data/bozer_neural_v0_4/train_fr.jsonl")
    parser.add_argument("--validation", default="data/bozer_neural_v0_4/validation_fr.jsonl")
    parser.add_argument("--test", default="data/bozer_neural_v0_4/test_fr.jsonl")
    args = parser.parse_args()
    result = validate_splits(args.train, args.validation, args.test)
    for split, counts in result.items():
        print(f"{split}: {sum(counts.values())} exemples")
        for category in sorted(counts):
            print(f"  {category}: {counts[category]}")


if __name__ == "__main__":
    main()
