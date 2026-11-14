"""Human-reviewed feedback loop for BOZER NEURAL."""
import argparse
import hashlib
import json
from pathlib import Path

CATEGORIES = {
    "dialogue_naturel", "comprehension_demande", "raisonnement_resolution",
    "planification_missions", "utilisation_outils_verification",
    "securite_confidentialite_honnetete",
}


def _clean_record(category, user, assistant):
    if category not in CATEGORIES:
        raise ValueError(f"unknown category: {category}")
    if not isinstance(user, str) or not user.strip():
        raise ValueError("user prompt must be non-empty")
    if not isinstance(assistant, str) or not assistant.strip():
        raise ValueError("assistant answer must be non-empty")
    user, assistant = user.strip(), assistant.strip()
    fingerprint = hashlib.sha256(
        (category + chr(0) + user.casefold()).encode("utf-8")
    ).hexdigest()[:16]
    return {"id": fingerprint, "category": category, "user": user,
            "assistant": assistant, "origin": "human_reviewed_feedback"}


def _read_jsonl(path):
    path = Path(path)
    if not path.exists():
        return []
    rows = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSONL at {path}:{number}: {exc}") from exc
        if not isinstance(row, dict):
            raise ValueError(f"record at {path}:{number} must be an object")
        rows.append(row)
    return rows


def _write_jsonl(path, rows):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + chr(10) for row in rows),
        encoding="utf-8",
    )
    temporary.replace(path)


def submit_feedback(pending_path, category, user, assistant):
    """Queue a proposed example; does not modify training data or model weights."""
    record = _clean_record(category, user, assistant)
    pending = _read_jsonl(pending_path)
    if any(row.get("id") == record["id"] for row in pending):
        raise ValueError("this prompt is already waiting for review")
    pending.append(record)
    _write_jsonl(pending_path, pending)
    return record["id"]


def approve_feedback(pending_path, training_path, record_id=None):
    """Explicitly promote reviewed examples into the training split only."""
    training_path = Path(training_path)
    target_name = training_path.name.casefold()
    if any(marker in target_name for marker in ("test", "validation", "val")):
        raise ValueError("feedback can only be approved into a training file")
    if "train" not in target_name:
        raise ValueError("target filename must clearly identify the training split")
    pending = _read_jsonl(pending_path)
    if not pending:
        raise ValueError("there is no pending feedback to approve")
    selected = [row for row in pending if record_id is None or row.get("id") == record_id]
    if record_id is not None and not selected:
        raise ValueError(f"pending feedback id not found: {record_id}")
    existing = _read_jsonl(training_path)
    known = {(str(row.get("category", "")), str(row.get("user", "")).strip().casefold())
             for row in existing}
    added, skipped = 0, 0
    selected_ids = {row["id"] for row in selected}
    for row in selected:
        key = (row["category"], row["user"].strip().casefold())
        if key in known:
            skipped += 1
        else:
            existing.append(row)
            known.add(key)
            added += 1
    _write_jsonl(training_path, existing)
    _write_jsonl(pending_path, [row for row in pending if row.get("id") not in selected_ids])
    return {"approved": added, "duplicates_skipped": skipped,
            "pending": len(pending) - len(selected)}


def main():
    parser = argparse.ArgumentParser(description="Controlled BOZER NEURAL feedback learning")
    sub = parser.add_subparsers(dest="command", required=True)
    add = sub.add_parser("submit", help="queue an example for human review")
    add.add_argument("--category", required=True, choices=sorted(CATEGORIES))
    add.add_argument("--user", required=True)
    add.add_argument("--assistant", required=True)
    add.add_argument("--pending", default="data/bozer_learning/pending_feedback.jsonl")
    approve = sub.add_parser("approve", help="promote reviewed examples into training data")
    approve.add_argument("--pending", default="data/bozer_learning/pending_feedback.jsonl")
    approve.add_argument("--training", default="data/bozer_neural_v0_3/train_fr.jsonl")
    approve.add_argument("--id", dest="record_id")
    args = parser.parse_args()
    if args.command == "submit":
        print("queued_feedback_id=" + submit_feedback(
            args.pending, args.category, args.user, args.assistant))
    else:
        print(json.dumps(approve_feedback(args.pending, args.training, args.record_id)))


if __name__ == "__main__":
    main()
