import unittest

from bozer_neural.language.validate_lexicon import validate_payload


def payload(entries):
    return {"format": "bozer-moore-french-lexicon-v1", "entries": entries}


class MooreLexiconValidationTests(unittest.TestCase):
    def test_current_lexicon_structure_is_valid(self):
        entry = {
            "id": "candidate-1",
            "french": "Bonjour",
            "moore": "FORME_A_VERIFIER",
            "status": "candidate",
            "source": "Original candidate contribution",
            "reviewed_by": None,
        }
        self.assertEqual(validate_payload(payload([entry])), [])

    def test_validated_entry_requires_provenance_and_review_date(self):
        entry = {
            "id": "validated-1",
            "french": "Bonjour",
            "moore": "FORME_VERIFIEE",
            "status": "validated",
            "source": "Native speaker fieldwork",
            "reviewed_by": "reviewer-01",
        }
        errors = validate_payload(payload([entry]))
        self.assertTrue(any("source_license" in error for error in errors))
        self.assertTrue(any("reviewed_at" in error for error in errors))

    def test_duplicate_ids_are_rejected(self):
        entry = {"id": "same", "french": "Bonjour", "moore": "X", "status": "candidate"}
        errors = validate_payload(payload([entry, entry]))
        self.assertTrue(any("id is duplicated" in error for error in errors))

    def test_unknown_status_is_rejected(self):
        entry = {"id": "x", "french": "Bonjour", "moore": "X", "status": "approved"}
        errors = validate_payload(payload([entry]))
        self.assertTrue(any("status is invalid" in error for error in errors))

    def test_missing_entries_array_is_rejected(self):
        self.assertIn("entries must be a list", validate_payload({"format": "bozer-moore-french-lexicon-v1"}))


if __name__ == "__main__":
    unittest.main()
