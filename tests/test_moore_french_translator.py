import unittest

from bozer_neural.language.moore_french import (
    MooreFrenchTranslator,
    load_default_translator,
    normalize_text,
)


class MooreFrenchTranslatorTests(unittest.TestCase):
    def test_normalization_preserves_moore_diacritics(self):
        self.assertEqual(normalize_text("  NE Y YIBEOOGO ! "), "ne y yibeoogo")
        self.assertEqual(normalize_text("wĩndga"), "wĩndga")

    def test_default_translation_exposes_candidates_as_provisional(self):
        translator = load_default_translator()
        result = translator.translate("Bonjour (le matin)", "fr", "mos")
        self.assertTrue(result.found)
        self.assertEqual(result.translated_text, "Ne y yibeoogo")
        self.assertEqual(result.status, "candidate")
        self.assertEqual(result.confidence, "provisional_exact")
        self.assertTrue(result.provisional)
        self.assertIn("native-speaker review", result.note)

    def test_strict_mode_excludes_candidates(self):
        translator = load_default_translator(allow_candidates=False)
        result = translator.translate("Bonjour (le matin)", "fr", "mos")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "not_found")

    def test_candidate_can_be_inspected_explicitly(self):
        translator = load_default_translator(allow_candidates=True)
        result = translator.translate("Bonjour (le matin)", "fr", "mos")
        self.assertTrue(result.found)
        self.assertEqual(result.translated_text, "Ne y yibeoogo")
        self.assertEqual(result.status, "candidate")
        self.assertEqual(result.confidence, "provisional_exact")

    def test_bulk_malm_candidates_load_by_default(self):
        default = load_default_translator()
        result = default.translate("chien", "fr", "mos")
        self.assertEqual(result.translated_text, "baaga¹")
        self.assertEqual(result.status, "candidate")
        self.assertEqual(result.confidence, "provisional_exact")
        self.assertIn("MALM Data", result.source)
        self.assertIn("MIT", result.source_license)
        self.assertIn("SRC-", result.source_reference)

    def test_strict_mode_does_not_use_bulk_malm_candidates(self):
        strict = load_default_translator(allow_candidates=False)
        self.assertEqual(strict.translate("chien", "fr", "mos").status, "not_found")

    def test_bulk_malm_candidates_are_bidirectional(self):
        translator = load_default_translator()
        result = translator.translate("baaga¹", "mos", "fr")
        self.assertEqual(result.translated_text, "chien")
        self.assertEqual(result.status, "candidate")
        self.assertEqual(result.confidence, "provisional_exact")

    def test_french_gloss_with_multiple_moore_forms_returns_ambiguous(self):
        translator = load_default_translator()
        result = translator.translate("femme", "fr", "mos")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "ambiguous")
        self.assertGreaterEqual(len(result.alternatives), 2)

    def test_reverse_lookup(self):
        translator = load_default_translator()
        result = translator.translate("Ne y yibeoogo", "mos", "fr")
        self.assertEqual(result.translated_text, "Bonjour (le matin)")
        self.assertEqual(result.status, "candidate")

    def test_polysemous_form_returns_ambiguous_instead_of_guessing(self):
        translator = MooreFrenchTranslator([
            {"id": "a", "french": "balai", "moore": "saaga", "status": "candidate"},
            {"id": "b", "french": "pluie", "moore": "saaga", "status": "candidate"},
        ])
        result = translator.translate("saaga", "mos", "fr")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "ambiguous")
        self.assertEqual(set(result.alternatives), {"balai", "pluie"})

    def test_unknown_phrase_is_not_guessed(self):
        translator = load_default_translator()
        result = translator.translate("Je veux rentrer à la maison", "fr", "mos")
        self.assertFalse(result.found)
        self.assertEqual(result.status, "not_found")

    def test_validated_entry_requires_source_and_native_reviewer(self):
        with self.assertRaisesRegex(ValueError, "source and native-speaker reviewer"):
            MooreFrenchTranslator([{
                "id": "bad", "french": "bonjour", "moore": "x",
                "status": "validated"
            }])

    def test_same_language_is_unchanged(self):
        result = load_default_translator().translate("Bonjour", "fr", "fr")
        self.assertEqual(result.translated_text, "Bonjour")
        self.assertEqual(result.status, "unchanged")

    def test_unknown_language_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "supported language codes"):
            load_default_translator().translate("Hello", "en", "mos")


if __name__ == "__main__":
    unittest.main()
