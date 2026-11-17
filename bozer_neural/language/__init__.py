"""Language tools shared by BOZER IA, NEURAL and TTS."""
from .dictionary import DictionaryHit, MooreFrenchDictionary, load_default_dictionary
from .moore_french import MooreFrenchTranslator, TranslationResult, load_default_translator

__all__ = [
    "DictionaryHit",
    "MooreFrenchDictionary",
    "MooreFrenchTranslator",
    "TranslationResult",
    "load_default_dictionary",
    "load_default_translator",
]
