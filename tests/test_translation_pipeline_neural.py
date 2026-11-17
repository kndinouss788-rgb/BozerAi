import unittest

from bozer_neural.language.moore_french import load_default_translator
from bozer_neural.language.translation_pipeline import (
    TranslationPipeline, TranslationRequest,
)


class FakeSentenceBackend:
    model_id = "test/fake-moore-model"
    license_id = "Apache-2.0"

    def translate(self, text, source_language, target_language):
        return "Ne y yibeoogo"


class TranslationPipelineNeuralTests(unittest.TestCase):
    def test_neural_backend_handles_unknown_full_sentence_and_requires_review(self):
        pipeline = TranslationPipeline(load_default_translator(), FakeSentenceBackend())
        response = pipeline.translate(TranslationRequest(
            "Bonjour, comment allez-vous ?", "fr", "mos"
        ))
        self.assertEqual(response.result.translated_text, "Ne y yibeoogo")
        self.assertEqual(response.result.status, "model")
        self.assertEqual(response.result.confidence, "model_generated")
        self.assertTrue(response.sentence_level_supported)
        self.assertTrue(response.requires_human_review)
        self.assertEqual(response.result.native_review_status, "pending")

    def test_dictionary_match_does_not_get_overwritten_by_neural_backend(self):
        pipeline = TranslationPipeline(load_default_translator(), FakeSentenceBackend())
        response = pipeline.translate(TranslationRequest("chien", "fr", "mos"))
        self.assertEqual(response.result.translated_text, "baaga¹")
        self.assertEqual(response.result.status, "candidate")
        self.assertFalse(response.sentence_level_supported)


if __name__ == "__main__":
    unittest.main()
