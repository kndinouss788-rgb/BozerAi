"""Validate categorized JSONL data, duplicates and optional scenario leakage."""
import argparse
import gzip
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


def is_dialogue_path(path):
    path = Path(path)
    return path.is_dir() or path.name.lower().endswith((".jsonl", ".jsonl.gz"))


def _dialogue_files(path):
    path = Path(path)
    if path.is_dir():
        files = sorted(path.glob("*.jsonl")) + sorted(path.glob("*.jsonl.gz"))
        if not files:
            raise ValueError(f"{path}: no JSONL dialogue shards found")
        return files
    if not is_dialogue_path(path):
        raise ValueError(f"{path}: expected JSONL dialogue data")
    return [path]


def normalize_prompt(value):
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return re.sub(r"\s+", " ", normalized).strip()


def read_records(path):
    records = []
    for source_path in _dialogue_files(path):
        opener = gzip.open if source_path.name.lower().endswith(".jsonl.gz") else open
        with opener(source_path, "rt", encoding="utf-8") as handle:
            for line_number, raw in enumerate(handle, 1):
                if not raw.strip():
                    continue
                try:
                    item = json.loads(raw)
                except json.JSONDecodeError as exc:
                    raise ValueError(
                        f"{source_path}:{line_number}: invalid JSON: {exc}"
                    ) from exc
                if not isinstance(item, dict):
                    raise ValueError(f"{source_path}:{line_number}: expected object")
                for key in ("category", "user", "assistant"):
                    if not isinstance(item.get(key), str) or not item[key].strip():
                        raise ValueError(
                            f"{source_path}:{line_number}: missing/invalid {key}"
                        )
                if item["category"] not in CATEGORIES:
                    raise ValueError(
                        f"{source_path}:{line_number}: unknown category {item['category']}"
                    )
                history = item.get("history", [])
                if not isinstance(history, list):
                    raise ValueError(
                        f"{source_path}:{line_number}: history must be a list"
                    )
                for turn_number, turn in enumerate(history, 1):
                    if not isinstance(turn, dict):
                        raise ValueError(
                            f"{source_path}:{line_number}: history turn {turn_number} must be an object"
                        )
                    for key in ("user", "assistant"):
                        if not isinstance(turn.get(key), str) or not turn[key].strip():
                            raise ValueError(
                                f"{source_path}:{line_number}: history turn {turn_number} needs non-empty {key}"
                            )
                scenario_id = item.get("scenario_id")
                if scenario_id is not None and (
                    not isinstance(scenario_id, str) or not scenario_id.strip()
                ):
                    raise ValueError(
                        f"{source_path}:{line_number}: scenario_id must be a non-empty string"
                    )
                records.append(item)
    if not records:
        raise ValueError(f"{path}: no records")
    return records


def validate_splits(train_path, validation_path, test_path):
    paths = {
        "train": Path(train_path),
        "validation": Path(validation_path),
        "test": Path(test_path),
    }
    dialogue_modes = {is_dialogue_path(path) for path in paths.values()}
    if dialogue_modes != {True}:
        if True in dialogue_modes:
            raise ValueError("all pretraining partitions must be plain text, not a mix of JSONL and text")
        resolved = [path.resolve() for path in paths.values()]
        if len(set(resolved)) != 3:
            raise ValueError("train, validation and test must be three different files")
        unit_owners = {}
        report = {}
        for name, path in paths.items():
            text = path.read_text(encoding="utf-8")
            units = {
                normalize_prompt(part)
                for part in text.replace("\r\n", "\n").split("\n\n")
                if normalize_prompt(part)
            }
            if not units:
                raise ValueError(f"{name} plain-text corpus is empty: {path}")
            report[name] = {"text_blocks": len(units)}
            for unit in units:
                previous = unit_owners.get(unit)
                if previous is not None and previous != name:
                    raise ValueError(
                        f"exact text-block leakage between {previous} and {name}"
                    )
                unit_owners.setdefault(unit, name)
        return report

    splits = {
        "train": read_records(paths["train"]),
        "validation": read_records(paths["validation"]),
        "test": read_records(paths["test"]),
    }
    prompt_owner = {}
    all_turn_prompt_owner = {}
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
            # History is context too: the same dialogue turn must not leak
            # from training into validation or the untouched test partition.
            dialogue_prompts = [
                *(turn["user"] for turn in item.get("history", [])),
                item["user"],
            ]
            for dialogue_prompt in dialogue_prompts:
                dialogue_key = normalize_prompt(dialogue_prompt)
                previous_split = all_turn_prompt_owner.get(dialogue_key)
                if previous_split is not None and previous_split != name:
                    raise ValueError(
                        f"dialogue-history leakage between {previous_split} and {name}: "
                        f"{dialogue_prompt}"
                    )
                all_turn_prompt_owner.setdefault(dialogue_key, name)
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
