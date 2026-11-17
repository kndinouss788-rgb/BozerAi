import unittest

from bozer_neural.language.dictionary import load_default_dictionary


class MooreFrenchDictionaryTests(unittest.TestCase):
    def test_default_dictionary_exposes_bulk_candidate_entries(self):
        dictionary = load_default_dictionary()
        hits = dictionary.exact("chien", "fr")
        self.assertTrue(hits)
        self.assertEqual(hits[0].translation, "baaga¹")
        self.assertEqual(hits[0].status, "candidate")
        self.assertTrue(hits[0].provisional)
        self.assertIn("MALM Data", hits[0].source)
        self.assertIn("MIT", hits[0].source_license)
        self.assertIn("SRC-", hits[0].source_reference)
        self.assertIn("LEX-", hits[0].source_reference)
        self.assertEqual(hits[0].rights_status, "pending_underlying_source_confirmation")
        self.assertEqual(hits[0].native_review_status, "pending")

    def test_dictionary_works_in_reverse_direction(self):
        dictionary = load_default_dictionary()
        hits = dictionary.exact("baaga¹", "mos")
        self.assertTrue(hits)
        self.assertEqual(hits[0].translation, "chien")
        self.assertEqual(hits[0].language, "mos")

    def test_strict_dictionary_hides_candidates(self):
        dictionary = load_default_dictionary(include_candidates=False)
        self.assertEqual(dictionary.exact("chien", "fr"), ())

    def test_substring_search_returns_limited_results(self):
        dictionary = load_default_dictionary()
        hits = dictionary.search("chien", "fr", limit=3)
        self.assertGreaterEqual(len(hits), 1)
        self.assertLessEqual(len(hits), 3)

    def test_unknown_language_is_rejected(self):
        dictionary = load_default_dictionary()
        with self.assertRaisesRegex(ValueError, "language must be"):
            dictionary.exact("chien", "en")


if __name__ == "__main__":
    unittest.main()
