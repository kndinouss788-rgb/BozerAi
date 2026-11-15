import unittest
from bozer_neural.language.audit_malm_candidates import audit_rows

class MalmQualityAuditTests(unittest.TestCase):
    def test_counts_synonyms_and_routes_them_to_review(self):
        report, queue = audit_rows([
            {"id": "1", "moore": "pog", "french": "femme", "sources": ["SRC-17"]},
            {"id": "2", "moore": "pug", "french": "femme", "sources": ["SRC-17"]},
        ])
        self.assertEqual(report["duplicate_exact_pairs"], 0)
        self.assertEqual(report["french_glosses_with_multiple_moore_forms"], 1)
        self.assertEqual(report["review_queue_entries"], 2)
        self.assertTrue(all("multiple_moore_forms_for_french_gloss" in x["flags"] for x in queue))

    def test_symbolic_and_metadata_entries_enter_review_queue(self):
        report, queue = audit_rows([
            {"id": "1", "moore": "-a", "french": "Part.", "sources": ["SRC-17"]},
            {"id": "2", "moore": "baaga", "french": "chien", "sources": ["SRC-17"]},
        ])
        self.assertEqual(report["review_queue_entries"], 1)
        self.assertIn("morpheme_or_symbolic_form", queue[0]["flags"])
        self.assertIn("technical_or_metadata_gloss", queue[0]["flags"])

    def test_same_moore_form_with_two_glosses_is_reported_and_queued(self):
        report, queue = audit_rows([
            {"id": "1", "moore": "saaga", "french": "balai", "sources": ["SRC-17"]},
            {"id": "2", "moore": "saaga", "french": "pluie", "sources": ["SRC-17"]},
        ])
        self.assertEqual(report["ambiguous_moore_forms"], 1)
        self.assertEqual(report["review_queue_entries"], 2)
        self.assertTrue(all("ambiguous_moore_form" in x["flags"] for x in queue))

    def test_exact_duplicates_are_reported_as_data_errors(self):
        row = {"id": "same", "moore": "baaga", "french": "chien", "sources": ["SRC-17"]}
        report, queue = audit_rows([row, row])
        self.assertEqual(report["duplicate_ids"], 1)
        self.assertEqual(report["duplicate_exact_pairs"], 1)

    def test_no_row_is_auto_validated(self):
        report, queue = audit_rows([
            {"id": "1", "moore": "baaga", "french": "chien", "status": "candidate"}
        ])
        self.assertEqual(report["native_validated_entries"], 0)
        self.assertTrue(report["policy"]["automatic_promotion_forbidden"])

if __name__ == "__main__":
    unittest.main()
