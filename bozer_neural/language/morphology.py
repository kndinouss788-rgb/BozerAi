"""Declarative morphology engine with no built-in unverified Mooré grammar.

Rules must be supplied from documented linguistic work and native-speaker review.
The engine only applies literal prefix/suffix rules; it does not guess spelling
alternations, tones, or grammatical functions.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class MorphologyRule:
    rule_id: str
    language: str
    affix_type: str
    affix: str
    feature: str
    source: str
    status: str
    reviewed_by: str | None = None

    def __post_init__(self):
        if not all(v.strip() for v in (self.rule_id, self.language, self.affix,
                                       self.feature, self.source, self.status)):
            raise ValueError("morphology rule fields must be non-empty")
        if self.affix_type not in {"prefix", "suffix"}:
            raise ValueError("affix_type must be prefix or suffix")
        if self.status not in {"candidate", "validated", "rejected"}:
            raise ValueError("unsupported morphology status")
        if self.status == "validated" and not self.reviewed_by:
            raise ValueError("validated morphology rule requires a native-speaker reviewer")


@dataclass(frozen=True)
class MorphemeAnalysis:
    surface: str
    stem: str
    affix: str
    feature: str
    rule_id: str


class RuleBasedMorphology:
    def __init__(self, rules: Iterable[MorphologyRule], *, allow_candidates: bool = False):
        self.rules = tuple(
            rule for rule in rules
            if rule.status == "validated" or (
                allow_candidates and rule.status == "candidate"
            )
        )

    def analyze(self, word: str, language: str) -> tuple[MorphemeAnalysis, ...]:
        if not word.strip():
            return ()
        results = []
        for rule in self.rules:
            if rule.language != language or len(word) <= len(rule.affix):
                continue
            if rule.affix_type == "prefix" and word.startswith(rule.affix):
                stem = word[len(rule.affix):]
            elif rule.affix_type == "suffix" and word.endswith(rule.affix):
                stem = word[:-len(rule.affix)]
            else:
                continue
            results.append(MorphemeAnalysis(
                surface=word, stem=stem, affix=rule.affix,
                feature=rule.feature, rule_id=rule.rule_id,
            ))
        return tuple(results)
