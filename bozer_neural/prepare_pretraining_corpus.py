"""Prepare deterministic, source-isolated plain-text splits for BOZER pretraining.

Every source document is assigned wholly to exactly one split. Only ingest
public-domain, openly licensed, or otherwise authorized text. Rights metadata
is owner-supplied and is not independently verified by this tool.
"""
import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path

EMAIL_RE = re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")
URL_RE = re.compile(r"(?i)\b(?:https?://|www\.)\S+")
PHONE_RE = re.compile(r"(?<!\w)(?:\+?\d[\d .()/-]{7,}\d)(?!\w)")
MANIFEST_FORMAT = "bozer-source-manifest-v1"


def normalize_block(value):
    value = unicodedata.normalize("NFKC", value)
    value = value.replace("\r\n", "\n").replace("\r", "\n")
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r" *\n *", "\n", value)
    return value.strip()


def redact_obvious_personal_data(value):
    value = EMAIL_RE.sub("[EMAIL]", value)
    value = URL_RE.sub("[URL]", value)
    value = PHONE_RE.sub("[PHONE]", value)
    return value


def _split_for_hash(block):
    normalized = " ".join(unicodedata.normalize("NFKC", block).casefold().split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _source_metadata(input_dir, files, manifest_path):
    relative_paths = {path.relative_to(input_dir).as_posix() for path in files}
    if manifest_path is None:
        return {
            relative: {
                "source_id": relative,
                "title": relative,
                "author": "",
                "publisher": "",
                "source_url": "",
                "license": "UNVERIFIED",
                "license_url": "",
                "retrieved_at": "",
                "allowed_use": False,
                "attribution": "",
                "rights_status": "unverified_not_training_ready",
            }
            for relative in sorted(relative_paths)
        }

    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or manifest.get("format") != MANIFEST_FORMAT:
        raise ValueError(f"source manifest must use format {MANIFEST_FORMAT}")
    entries = manifest.get("sources")
    if not isinstance(entries, list) or not entries:
        raise ValueError("source manifest must contain a non-empty sources list")

    metadata = {}
    for index, entry in enumerate(entries, 1):
        if not isinstance(entry, dict):
            raise ValueError(f"source manifest entry {index} must be an object")
        relative = entry.get("path")
        source_id = entry.get("source_id")
        if not isinstance(relative, str) or relative not in relative_paths:
            raise ValueError(f"source manifest entry {index} has an unknown source path")
        if relative in metadata:
            raise ValueError(f"duplicate source manifest path: {relative}")
        if not isinstance(source_id, str) or not source_id.strip():
            raise ValueError(f"source manifest entry {index} needs a non-empty source_id")
        if any(item["source_id"] == source_id for item in metadata.values()):
            raise ValueError(f"duplicate source_id: {source_id}")
        for field in ("title", "author", "publisher", "source_url", "license",
                      "license_url", "retrieved_at", "attribution"):
            if not isinstance(entry.get(field, ""), str):
                raise ValueError(f"source manifest field {field} must be text")
        if not isinstance(entry.get("allowed_use"), bool):
            raise ValueError(f"source manifest entry {index} needs boolean allowed_use")
        allowed = entry["allowed_use"]
        license_name = entry.get("license", "").strip()
        license_url = entry.get("license_url", "").strip()
        metadata[relative] = {
            "source_id": source_id.strip(),
            "title": entry.get("title", "").strip(),
            "author": entry.get("author", "").strip(),
            "publisher": entry.get("publisher", "").strip(),
            "source_url": entry.get("source_url", "").strip(),
            "license": license_name or "UNVERIFIED",
            "license_url": license_url,
            "retrieved_at": entry.get("retrieved_at", "").strip(),
            "allowed_use": allowed,
            "attribution": entry.get("attribution", "").strip(),
            "rights_status": (
                "owner_declared_authorized"
                if allowed and license_name and license_url
                else "not_training_ready"
            ),
        }

    missing = relative_paths - set(metadata)
    extra = set(metadata) - relative_paths
    if missing or extra:
        raise ValueError(
            f"source manifest must cover every .txt file; missing={sorted(missing)}, "
            f"extra={sorted(extra)}"
        )
    return metadata


def prepare_corpus(input_dir, output_dir, min_chars=80, max_chars=12000,
                   train_percent=90, validation_percent=5, source_manifest=None):
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
    metadata = _source_metadata(input_dir, files, source_manifest)

    seen_blocks = set()
    documents = []
    counts = {"read": 0, "duplicate": 0, "too_short": 0, "too_long": 0}
    source_reports = []

    for path in files:
        relative = path.relative_to(input_dir).as_posix()
        raw_bytes = path.read_bytes()
        raw = raw_bytes.decode("utf-8-sig")
        document_blocks = []
        for raw_block in re.split(r"\n\s*\n+", raw):
            block = normalize_block(raw_block)
            if not block:
                continue
            block = normalize_block(redact_obvious_personal_data(block))
            counts["read"] += 1
            if len(block) < min_chars:
                counts["too_short"] += 1
                continue
            if len(block) > max_chars:
                counts["too_long"] += 1
                continue
            digest = _split_for_hash(block)
            if digest in seen_blocks:
                counts["duplicate"] += 1
                continue
            seen_blocks.add(digest)
            document_blocks.append((digest, block))

        source = dict(metadata[relative])
        source.update({
            "source_file": relative,
            "source_sha256": hashlib.sha256(raw_bytes).hexdigest(),
            "unique_blocks": len(document_blocks),
        })
        source_reports.append(source)
        if document_blocks:
            documents.append((source["source_id"], document_blocks, source))

    splits = {"train": [], "validation": [], "test": []}
    split_sources = {"train": [], "validation": [], "test": []}
    for source_id, blocks, source_report in documents:
        bucket = int(hashlib.sha256(source_id.encode("utf-8")).hexdigest()[:8], 16) % 100
        if bucket < train_percent:
            split = "train"
        elif bucket < train_percent + validation_percent:
            split = "validation"
        else:
            split = "test"
        splits[split].extend(blocks)
        split_sources[split].append(source_report["source_file"])
        source_report["split"] = split

    if any(not splits[name] for name in splits):
        sizes = {name: len(items) for name, items in splits.items()}
        source_sizes = {name: len(items) for name, items in split_sources.items()}
        raise ValueError(
            "not enough source documents to populate all source-isolated splits: "
            f"blocks={sizes}, source_documents={source_sizes}; add more independent files"
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    training_ready = all(
        source["unique_blocks"] == 0 or source["rights_status"] == "owner_declared_authorized"
        for source in source_reports
    )
    report = {
        "format": "bozer-pretraining-corpus-v2",
        "split_method": "sha256_source_id_mod_100_source_document_grouping",
        "train_percent": train_percent,
        "validation_percent": validation_percent,
        "test_percent": 100 - train_percent - validation_percent,
        "counts": counts,
        "source_documents": len(documents),
        "split_source_documents": {name: len(items) for name, items in split_sources.items()},
        "split_blocks": {name: len(items) for name, items in splits.items()},
        "split_characters": {
            name: sum(len(block) for _, block in items)
            for name, items in splits.items()
        },
        "sources": source_reports,
        "training_ready": training_ready,
        "rights_warning": (
            "Owner-provided license metadata is not independently verified. "
            "Do not train until every included source is lawfully authorized."
        ),
        "privacy_redactions": ["email", "url", "phone-like sequences"],
    }
    for name, items in splits.items():
        items.sort(key=lambda pair: pair[0])
        content = "\n\n".join(block for _, block in items) + "\n"
        destination = output_dir / f"{name}.txt"
        destination.write_text(content, encoding="utf-8")
        report.setdefault("output_sha256", {})[destination.name] = hashlib.sha256(
            content.encode("utf-8")
        ).hexdigest()

    (output_dir / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return report


def main():
    parser = argparse.ArgumentParser(
        description="Prepare source-isolated text partitions for BOZER pretraining."
    )
    parser.add_argument("--input-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--min-chars", type=int, default=80)
    parser.add_argument("--max-chars", type=int, default=12000)
    parser.add_argument("--train-percent", type=int, default=90)
    parser.add_argument("--validation-percent", type=int, default=5)
    parser.add_argument("--source-manifest", help="JSON provenance/rights manifest")
    args = parser.parse_args()
    report = prepare_corpus(
        args.input_dir, args.output_dir, args.min_chars, args.max_chars,
        args.train_percent, args.validation_percent, args.source_manifest,
    )
    print(json.dumps({
        "split_blocks": report["split_blocks"],
        "split_source_documents": report["split_source_documents"],
        "training_ready": report["training_ready"],
    }, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
