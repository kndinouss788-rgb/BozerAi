"""Safe translation pipeline around the reviewed Mooré/French lexicon.

It intentionally does not concatenate word translations into fabricated
sentences. Sentence translation requires a reviewed corpus/model and evaluation.
"""
from dataclasses import dataclass
from .moore_french import MooreFrenchTranslator, TranslationResult

@dataclass(frozen=True)
class TranslationRequest:
    text: str
    source_language: str
    target_language: str

@dataclass(frozen=True)
class TranslationResponse:
    result: TranslationResult
    sentence_level_supported: bool = False
    requires_human_review: bool = False

class TranslationPipeline:
    def __init__(self, translator: MooreFrenchTranslator):
        self.translator = translator

    def translate(self, request: TranslationRequest) -> TranslationResponse:
        result = self.translator.translate(
            request.text, request.source_language, request.target_language
        )
        return TranslationResponse(
            result=result,
            sentence_level_supported=False,
            requires_human_review=result.status in {"not_found", "ambiguous", "candidate"},
        )
