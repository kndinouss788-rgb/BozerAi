import os
import tempfile
import unittest

from bozer_brain.dialogue import DialogueEngine
from bozer_brain.memory import ConversationMemory
from bozer_brain.model_gateway import ModelGateway, ModelResponse
from bozer_brain.models import IntentKind


class FakeProvider:
    provider_name = "test-provider"

    def generate(self, request):
        return ModelResponse("Réponse IA test", self.provider_name, "fake")


class MemoryTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.memory = ConversationMemory(os.path.join(self.temp_dir.name, "bozer.sqlite3"))

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_turns_persist_and_keep_order(self):
        self.memory.add_turn("user", "Salut")
        self.memory.add_turn("assistant", "Bonjour")
        self.assertEqual(
            self.memory.recent_turns(),
            [("user", "Salut"), ("assistant", "Bonjour")],
        )

    def test_fact_upsert_and_forget(self):
        self.memory.remember_fact("language", "français")
        self.memory.remember_fact("language", "français simple")
        self.assertEqual(self.memory.get_fact("language"), "français simple")
        self.assertTrue(self.memory.forget_fact("language"))
        self.assertIsNone(self.memory.get_fact("language"))

    def test_reject_invalid_turn(self):
        with self.assertRaises(ValueError):
            self.memory.add_turn("system", "not allowed")


class DialogueTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.memory = ConversationMemory(os.path.join(self.temp_dir.name, "dialogue.sqlite3"))

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_action_is_planned_not_executed(self):
        result = DialogueEngine(self.memory).respond("Appelle Ali")
        self.assertEqual(result.proposal.action, IntentKind.MAKE_CALL.value)
        self.assertTrue(result.proposal.requires_confirmation)
        self.assertFalse(result.used_language_model)
        self.assertEqual(len(self.memory.recent_turns()), 2)

    def test_model_gateway_requires_per_call_opt_in(self):
        engine = DialogueEngine(self.memory, model_gateway=ModelGateway(FakeProvider()))
        blocked = engine.respond("Explique la gravité")
        self.assertFalse(blocked.used_language_model)
        self.assertEqual(blocked.model_error, "provider_use_not_enabled")
        allowed = engine.respond("Explique encore", allow_model=True)
        self.assertTrue(allowed.used_language_model)
        self.assertEqual(allowed.reply, "Réponse IA test")

    def test_legacy_responder_receives_context_only_when_enabled(self):
        captured = []
        self.memory.add_turn("user", "Je préfère les réponses en français.")
        engine = DialogueEngine(
            self.memory, responder=lambda prompt: captured.append(prompt) or "Réponse du modèle"
        )
        result = engine.respond("Et peux-tu expliquer la gravité ?", allow_model=True)
        self.assertEqual(result.reply, "Réponse du modèle")
        self.assertTrue(result.used_language_model)
        self.assertIn("Je préfère les réponses en français.", captured[0])
        self.assertIn("Et peux-tu expliquer la gravité ?", captured[0])

    def test_without_model_does_not_fake_ai(self):
        result = DialogueEngine(self.memory).respond("Explique la gravité")
        self.assertFalse(result.used_language_model)
        self.assertIn("pas encore connecté", result.reply)
        self.assertEqual(result.model_error, "provider_not_configured")


if __name__ == "__main__":
    unittest.main()
