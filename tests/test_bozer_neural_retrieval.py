"""Tests for conservative, corpus-grounded retrieval memory."""
import json

from bozer_neural.retrieval import BozerRetrievalMemory, normalize_query


def _write_corpus(path, rows):
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )


def test_normalization_handles_case_accents_and_punctuation():
    assert normalize_query("  BONJOUR, BOZER !  ") == "bonjour bozer"


def test_exact_match_returns_grounded_answer(tmp_path):
    corpus = tmp_path / "train.jsonl"
    _write_corpus(corpus, [{
        "category": "dialogue_naturel",
        "user": "Comment vas-tu BOZER ?",
        "assistant": "Je vais bien, merci.",
    }])
    memory = BozerRetrievalMemory.from_corpus(corpus)

    result = memory.retrieve("COMMENT VAS-TU BOZER!")

    assert result is not None
    assert result["answer"] == "Je vais bien, merci."
    assert result["match_type"] == "exact"
    assert result["score"] == 1.0


def test_similar_query_requires_high_confidence_and_clear_margin():
    memory = BozerRetrievalMemory([{
        "category": "dialogue_naturel",
        "user": "Comment vas-tu BOZER ?",
        "assistant": "Je vais bien, merci.",
    }], minimum_score=0.50, minimum_margin=0.01)

    result = memory.retrieve("Comment est-ce que tu vas BOZER ?")

    assert result is not None
    assert result["answer"] == "Je vais bien, merci."


def test_unknown_or_ambiguous_queries_abstain():
    memory = BozerRetrievalMemory([
        {"category": "a", "user": "Comment vas-tu BOZER ?", "assistant": "Je vais bien."},
        {"category": "b", "user": "Comment vas-tu BOZER ?", "assistant": "Très bien."},
        {"category": "c", "user": "Quel temps fait-il ?", "assistant": "Je ne sais pas."},
    ])

    assert memory.retrieve("Question totalement différente sur les galaxies") is None
    assert memory.retrieve("Comment vas-tu BOZER ?") is None


def test_short_queries_abstain():
    memory = BozerRetrievalMemory([{
        "category": "dialogue_naturel", "user": "Bonjour BOZER",
        "assistant": "Bonjour !",
    }])
    assert memory.retrieve("oui") is None
