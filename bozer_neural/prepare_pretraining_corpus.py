"""Prepare deterministic, deduplicated plain-text splits for BOZER 1B pretraining.

Only ingest text that is public-domain, openly licensed, or otherwise authorized.
The tool does not fetch data or grant rights to any source.
"""
import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path

EMAIL_RE = re.compile(r"(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
URL_RE = re.compile(r"(?i)\\b(?:https?://|www\\.)\\S+")
PHONE_RE = re.compile(r"(?<!\\w)(?:\\+?\\d[\\d .()/-]{7,}\\d)(?!\\w)")


def normalize_block(value):
    value = unicodedata.normalize("NFKC", value)
    value = value.replace("\\r\\n", "\\n").replace("\\r", "\\n")
    value = re.sub(r"[ \\t]+", " ", value)
    value = re.sub(r" *\\n *", "\\n", value)
    return value.strip()


def redact_obvious_personal_data(value):
    value = EMAIL_RE.sub("[EMAIL]", value)
    value = URL_RE.sub("[URL]", value)
    value = PHONE_RE.sub("[PHONE]", value)
    return value


def _split_for_hash(block):
    normalized = " ".join(unicodedata.normalize("NFKC", block).casefold().split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def prepare_corpus(input_dir, output_dir, min_chars=80, max_chars=12000,
                   train_percent=90, validation_percent=5):
    input_dir = Path(input_dir)
    output_dir = Path(output_dir)
    if not input_dir.is_dir():
        raise ValueError(f"input directory does not exist: {input_dir}")
    if min_chars < 1 or max_chars < min_chars:
        raise ValueError("invalid paragraph length limits")
    if train_percent < 1 or validation_percent < 1:
        raise ValueError("train and validation percentages must be positive")
    if train_percent + validation_percent >= 100:
        raise ValueError("train + validation percentages must leave room for test")

    files = sorted(path for path in input_dir.rglob("*.txt") if path.is_file())
    if not files:
        raise ValueError("no .txt source files found")

    seen = set()
    splits = {"train": [], "validation": [], "test": []}
    counts = {"read": 0, "duplicate": 0, "too_short": 0, "too_long": 0}
    sources = []

    for path in files:
        raw = path.read_text(encoding="utf-8-sig")
        source_count = 0
        for raw_block in re.split(r"\\n\\s*\\n+", raw):
            block = normalize_block(raw_block)
            if not block:
                continue
            block = redact_obvious_personal_data(block)
            block = normalize_block(block)
            counts["read"] += 1
            if len(block) < min_chars:
                counts["too_short"] += 1
                continue
            if len(block) > max_chars:
                counts["too_long"] += 1
                continue
            digest = _split_for_hash(block)
            if digest in seen:
                counts["duplicate"] += 1
                continue
            seen.add(digest)
            bucket = int(digest[:8], 16) % 100
            if bucket < train_percent:
                split = "train"
            elif bucket < train_percent + validation_percent:
                split = "validation"
            else:
                split = "test"
            splits[split].append((digest, block))
            source_count += 1
        sources.append({"source_file": path.relative_to(input_dir).as_posix(),
                        "unique_blocks": source_count})

    if any(not splits[name] for name in splits):
        sizes = {name: len(items) for name, items in splits.items()}
        raise ValueError(f"not enough varied text to populate all splits: {sizes}")

    output_dir.mkdir(parents=True, exist_ok=True)
    report = {
        "format": "bozer-pretraining-corpus-v1",
        "split_method": "sha256_content_hash_mod_100",
        "train_percent": train_percent,
        "validation_percent": validation_percent,
        "test_percent": 100 - train_percent - validation_percent,
        "counts": counts,
        "split_blocks": {name: len(items) for name, items in splits.items()},
        "split_characters": {
            name: sum(len(block) for _, block in items)
            for name, items in splits.items()
        },
        "sources": sources,
        "privacy_redactions": ["email", "url", "phone-like sequences"],
        "license_note": "Source rights must be checked by the corpus owner before ingestion.",
    }
    for name, items in splits.items():
        items.sort(key=lambda pair: pair[0])
        content = "\\n\\n".join(block for _, block in items) + "\\n"
        destination = output_dir / f"{name}.txt"
        destination.write_text(content, encoding="utf-8")
        report.setdefault("output_sha256", {})[destination.name] = hashlib.sha256(
            content.encode("utf-8")
        ).hexdigest()

    (output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\\n",
        encoding="utf-8",
    )
    return report


def main():
    parser = argparse.ArgumentParser(
        description="Prepare deduplicated train/validation/test text for BOZER 1B."
    )
    parser.add_argument("--input-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--min-chars", type=int, default=80)
    parser.add_argument("--max-chars", type=int, default=12000)
    parser.add_argument("--train-percent", type=int, default=90)
    parser.add_argument("--validation-percent", type=int, default=5)
    args = parser.parse_args()
    report = prepare_corpus(
        args.input_dir, args.output_dir, args.min_chars, args.max_chars,
        args.train_percent, args.validation_percent,
    )
    print(json.dumps(report["split_blocks"], ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
