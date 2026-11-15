"""Language tools shared by BOZER IA, NEURAL and TTS."""
from .moore_french import MooreFrenchTranslator, TranslationResult, load_default_translator

__all__ = ["MooreFrenchTranslator", "TranslationResult", "load_default_translator"]
