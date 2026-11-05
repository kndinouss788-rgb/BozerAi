import os
import tempfile
import unittest

from bozer_brain.dialogue import DialogueEngine
from bozer_brain.eye_search import EyeSearch, SearchResult
from bozer_brain.memory import ConversationMemory
from bozer_brain.model_gateway import ModelGateway, ModelResponse


class FakeProvider:
    provider_name = "test-provider"

    def generate(self, request):
        return ModelResponse("Réponse IA test", self.provider_name, "fake")


class FakeSearchSource:
    source_name = "test-public"

    def __init__(self):
        self.calls = []

    def search(self, query, limit):
        self.calls.append(query)
        return [SearchResult("Document", "https://example.test/doc", "Extrait public")]


class DialogueResearchTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.memory = ConversationMemory(os.path.join(self.temp_dir.name, "dialogue.sqlite3"))

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_opted_in_research_is_added_to_model_prompt(self):
        source = FakeSearchSource()
        captured = []
        engine = DialogueEngine(
            self.memory,
            responder=lambda prompt: captured.append(prompt) or "Réponse documentée",
            eye_search=EyeSearch([source]),
        )
        result = engine.respond(
            "Explique ce sujet", allow_model=True, allow_search=True
        )
        self.assertTrue(result.used_language_model)
        self.assertEqual(result.reply, "Réponse documentée")
        self.assertEqual(source.calls, ["Explique ce sujet"])
        self.assertIn("Extrait public", captured[0])
        self.assertIsNotNone(result.research)

    def test_search_is_not_called_without_explicit_opt_in(self):
        source = FakeSearchSource()
        engine = DialogueEngine(self.memory, eye_search=EyeSearch([source]))
        engine.respond("Explique ce sujet")
        self.assertEqual(source.calls, [])

    def test_phone_action_never_triggers_research(self):
        source = FakeSearchSource()
        engine = DialogueEngine(self.memory, eye_search=EyeSearch([source]))
        result = engine.respond("Appelle Ali", allow_search=True)
        self.assertEqual(source.calls, [])
        self.assertIsNone(result.research)


if __name__ == "__main__":
    unittest.main()
