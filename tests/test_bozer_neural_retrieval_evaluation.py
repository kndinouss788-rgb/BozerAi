"""Tests for the held-out retrieval quality benchmark."""
import json

from bozer_neural.evaluate_retrieval import evaluate_retrieval


def _write(path, rows):
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )


def test_evaluator_reports_coverage_and_exact_answer_precision(tmp_path):
    train = tmp_path / "train.jsonl"
    test = tmp_path / "test.jsonl"
    _write(train, [
        {"category": "dialogue", "user": "Comment vas-tu BOZER ?", "assistant": "Je vais bien."},
        {"category": "dialogue", "user": "Quelle est la capitale du Burkina Faso ?", "assistant": "Ouagadougou."},
    ])
    _write(test, [
        {"category": "dialogue", "user": "Comment vas-tu BOZER ?", "assistant": "Je vais bien."},
        {"category": "dialogue", "user": "Une question inconnue sur les galaxies ?", "assistant": "Je ne sais pas."},
    ])

    result = evaluate_retrieval(train, test)

    assert result["training_examples"] == 2
    assert result["test_examples"] == 2
    assert result["retrieved"] == 1
    assert result["exact_answer_matches"] == 1
    assert result["coverage"] == 0.5
    assert result["precision_on_retrieved"] == 1.0
