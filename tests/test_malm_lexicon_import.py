import unittest
from bozer_neural.language.import_malm_lexicon import convert

class MalmLexiconImportTests(unittest.TestCase):
    def test_converts_only_as_candidates(self):
        result = convert({"entrées": [{"id": "LEX-1", "mooré": "baaga", "français": "chien", "source": "SRC-17"}]})
        self.assertEqual(len(result["entries"]), 1)
        self.assertEqual(result["entries"][0]["status"], "candidate")
        self.assertEqual(result["entries"][0]["moore"], "baaga")
    def test_skips_malformed_rows(self):
        result = convert({"entrées": [{"id": "bad", "mooré": "x"}, "bad", {"mooré": "paga", "français": "femme"}]})
        self.assertEqual(len(result["entries"]), 1)
    def test_requires_expected_shape(self):
        with self.assertRaisesRegex(ValueError, "entrées"):
            convert({"entries": []})

if __name__ == "__main__":
    unittest.main()