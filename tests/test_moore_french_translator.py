import json
import tempfile
import unittest
from pathlib import Path

from bozer_neural.language.moore_french import (
    MooreFrenchTranslator,
    load_default_translator,
    normalize_text,
)


class MooreFrenchTranslatorTests(unittest.TestCase):
    def test_normalization_preserves_moore_diacritics(self):
        self.assertEqual(normalize_text("  NE Y YIBEOOGO ! "), "ne y yibeoogo")
        self.assertEqual(normalize_text("wĩndga"), "wĩndga")

    def test_candidate_is_not_used_by_default(self):
        translator = load_default_translator()
        result = translator.translate("Bonjour (le matin)", "fr", "mos")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "not_found")

    def test_candidate_can_be_inspected_explicitly(self):
        translator = load_default_translator(allow_candidates=True)
        result = translator.translate("Bonjour (le matin)", "fr", "mos")
        self.assertTrue(result.found)
        self.assertEqual(result.translated_text, "Ne y yibeoogo")
        self.assertEqual(result.status, "candidate")
        self.assertEqual(result.confidence, "lexicon_exact")

    def test_reverse_lookup(self):
        translator = load_default_translator(allow_candidates=True)
        result = translator.translate("Ne y yibeoogo", "mos", "fr")
        self.assertEqual(result.translated_text, "Bonjour (le matin)")

    def test_unknown_phrase_is_not_guessed(self):
        translator = load_default_translator(allow_candidates=True)
        result = translator.translate("Je veux rentrer à la maison", "fr", "mos")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "not_found")

    def test_validated_entry_requires_source_and_native_reviewer(self):
        with self.assertRaisesRegex(ValueError, "source and native-speaker reviewer"):
            MooreFrenchTranslator([{
                "id": "bad", "french": "bonjour", "moore": "x",
                "status": "validated"
            }])

    def test_duplicate_keys_are_rejected(self):
        entry = {
            "id": "one", "french": "bonjour", "moore": "greeting",
            "status": "candidate"
        }
        with self.assertRaisesRegex(ValueError, "duplicate translation key"):
            MooreFrenchTranslator([entry, {**entry, "id": "two"}], allow_candidates=True)

    def test_same_language_is_unchanged(self):
        result = load_default_translator().translate("Bonjour", "fr", "fr")
        self.assertEqual(result.translated_text, "Bonjour")
        self.assertEqual(result.status, "unchanged")

    def test_unknown_language_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "supported language codes"):
            load_default_translator().translate("Hello", "en", "mos")


if __name__ == "__main__":
    unittest.main()
