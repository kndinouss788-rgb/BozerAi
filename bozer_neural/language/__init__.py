"""Language tools shared by BOZER IA, NEURAL and TTS."""
from .dictionary import DictionaryHit, MooreFrenchDictionary, load_default_dictionary
from .moore_french import MooreFrenchTranslator, TranslationResult, load_default_translator
from .translation_pipeline import TranslationPipeline, TranslationRequest, TranslationResponse

__all__ = [
    "DictionaryHit",
    "MooreFrenchDictionary",
    "MooreFrenchTranslator",
    "TranslationPipeline",
    "TranslationRequest",
    "TranslationResponse",
    "TranslationResult",
    "load_default_dictionary",
    "load_default_translator",
]
