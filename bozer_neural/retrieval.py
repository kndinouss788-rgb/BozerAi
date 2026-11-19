"""Grounded dialogue retrieval for BOZER's hybrid answer path.

This memory layer is deliberately conservative: it returns a corpus answer only
when the query is an exact match or a high-confidence, clearly separated match.
Otherwise it abstains and lets the neural generator handle the request.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import re
import unicodedata

from .data import read_records


def normalize_query(text: str) -> str:
    """Normalize Unicode, case, punctuation and whitespace for matching."""
    if not isinstance(text, str):
        raise TypeError("query must be text")
    value = unicodedata.normalize("NFKC", text).casefold()
    chars = []
    for char in value:
        category = unicodedata.category(char)
        chars.append(" " if char.isspace() or category.startswith("P") else char)
    return " ".join("".join(chars).split())


def _char_ngrams(text: str, n: int = 3) -> Counter:
    padded = f"  {text}  "
    return Counter(padded[index:index + n] for index in range(max(0, len(padded) - n + 1)))


def _cosine(left: Counter, right: Counter) -> float:
    if not left or not right:
        return 0.0
    dot = sum(value * right.get(key, 0) for key, value in left.items())
    left_norm = sum(value * value for value in left.values()) ** 0.5
    right_norm = sum(value * value for value in right.values()) ** 0.5
    return dot / (left_norm * right_norm)


def _similarity(left: str, right: str) -> float:
    left_words, right_words = set(left.split()), set(right.split())
    if not left_words or not right_words:
        return 0.0
    word_dice = 2 * len(left_words & right_words) / (len(left_words) + len(right_words))
    char_score = _cosine(_char_ngrams(left), _char_ngrams(right))
    return 0.65 * char_score + 0.35 * word_dice


class BozerRetrievalMemory:
    """Read-only retrieval index over approved dialogue examples."""

    def __init__(self, records, *, minimum_score: float = 0.88,
                 minimum_margin: float = 0.08):
        if not 0.0 <= minimum_score <= 1.0:
            raise ValueError("minimum_score must be between 0 and 1")
        if not 0.0 <= minimum_margin <= 1.0:
            raise ValueError("minimum_margin must be between 0 and 1")
        self.minimum_score = minimum_score
        self.minimum_margin = minimum_margin
        grouped = defaultdict(Counter)
        metadata = {}
        for item in records:
            question = normalize_query(item.get("user", ""))
            answer = item.get("assistant", "").strip()
            if not question or not answer:
                continue
            grouped[question][answer] += 1
            metadata[(question, answer)] = item.get("category", "general")
        if not grouped:
            raise ValueError("retrieval corpus contains no usable question/answer pairs")
        self._entries = []
        self._exact = {}
        for question, answers in grouped.items():
            answer, count = answers.most_common(1)[0]
            confidence = count / sum(answers.values())
            entry = {
                "question": question,
                "answer": answer,
                "category": metadata[(question, answer)],
                "consensus": confidence,
                "ambiguous": len(answers) > 1 and confidence < 0.8,
            }
            self._entries.append(entry)
            self._exact[question] = entry

    @classmethod
    def from_corpus(cls, path, **kwargs):
        """Load JSONL, compressed JSONL, or a directory of dialogue shards."""
        return cls(read_records(path), **kwargs)

    def retrieve(self, query: str):
        """Return a grounded match or None when evidence is insufficient."""
        normalized = normalize_query(query)
        if len(normalized.split()) < 2:
            return None

        exact = self._exact.get(normalized)
        if exact is not None:
            if exact["ambiguous"]:
                return None
            return {
                "answer": exact["answer"],
                "matched_question": exact["question"],
                "category": exact["category"],
                "score": 1.0,
                "match_type": "exact",
            }

        ranked = []
        for entry in self._entries:
            if entry["ambiguous"]:
                continue
            score = _similarity(normalized, entry["question"]) * entry["consensus"]
            ranked.append((score, entry))
        if not ranked:
            return None
        ranked.sort(key=lambda item: item[0], reverse=True)
        best_score, best = ranked[0]
        second_score = ranked[1][0] if len(ranked) > 1 else 0.0
        if best_score < self.minimum_score:
            return None
        if best_score - second_score < self.minimum_margin:
            return None
        return {
            "answer": best["answer"],
            "matched_question": best["question"],
            "category": best["category"],
            "score": best_score,
            "match_type": "similarity",
        }
