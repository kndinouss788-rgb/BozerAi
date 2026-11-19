"""Held-out quality benchmark for BOZER's corpus-grounded retrieval path."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from .data import read_records
from .retrieval import BozerRetrievalMemory, normalize_query


def evaluate_retrieval(train_path, test_path, *, limit=None, minimum_score=0.88,
                       minimum_margin=0.08):
    """Measure coverage and exact-answer precision without tuning on test data."""
    training = read_records(train_path)
    testing = read_records(test_path)
    if limit is not None:
        if limit < 1:
            raise ValueError("limit must be positive")
        testing = testing[:limit]

    memory = BozerRetrievalMemory(
        training,
        minimum_score=minimum_score,
        minimum_margin=minimum_margin,
    )
    retrieved = 0
    exact_answers = 0
    examples = []
    for row in testing:
        result = memory.retrieve(row["user"])
        if result is None:
            continue
        retrieved += 1
        matched = normalize_query(result["answer"]) == normalize_query(row["assistant"])
        exact_answers += int(matched)
        if len(examples) < 20:
            examples.append({
                "query": row["user"],
                "expected": row["assistant"],
                "retrieved": result["answer"],
                "score": round(result["score"], 4),
                "match_type": result["match_type"],
                "exact_answer_match": matched,
            })

    total = len(testing)
    return {
        "training_examples": len(training),
        "test_examples": total,
        "retrieved": retrieved,
        "coverage": retrieved / total if total else 0.0,
        "exact_answer_matches": exact_answers,
        "precision_on_retrieved": exact_answers / retrieved if retrieved else 0.0,
        "end_to_end_exact_accuracy": exact_answers / total if total else 0.0,
        "minimum_score": minimum_score,
        "minimum_margin": minimum_margin,
        "examples": examples,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--train", required=True)
    parser.add_argument("--test", required=True)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--minimum-score", type=float, default=0.88)
    parser.add_argument("--minimum-margin", type=float, default=0.08)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()

    report = evaluate_retrieval(
        args.train,
        args.test,
        limit=args.limit,
        minimum_score=args.minimum_score,
        minimum_margin=args.minimum_margin,
    )
    destination = Path(args.report)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        "retrieval_benchmark="
        f"coverage:{report['retrieved']}/{report['test_examples']} "
        f"({report['coverage']:.1%}), "
        f"precision:{report['exact_answer_matches']}/{report['retrieved']} "
        f"({report['precision_on_retrieved']:.1%}), "
        f"end_to_end:{report['exact_answer_matches']}/{report['test_examples']} "
        f"({report['end_to_end_exact_accuracy']:.1%})"
    )


if __name__ == "__main__":
    main()
