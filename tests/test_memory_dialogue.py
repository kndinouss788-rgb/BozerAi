import os
import tempfile
import unittest
from types import SimpleNamespace

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

    def test_from_environment_prefers_local_model_over_remote_credentials(self):
        engine = DialogueEngine.from_environment(
            self.memory,
            environ={
                "BOZER_LOCAL_MODEL_URL": "http://127.0.0.1:8080/v1",
                "BOZER_MODEL_API_KEY": "remote-secret",
                "BOZER_MODEL_NAME": "remote-model",
            },
        )
        self.assertEqual(engine.model_gateway.provider.provider_name, "local-llama-cpp")

    def test_from_environment_wires_native_provider_into_dialogue(self):
        engine = DialogueEngine.from_environment(
            self.memory,
            environ={"BOZER_MODEL_API_KEY": "test-secret", "BOZER_MODEL_NAME": "test-model"},
        )
        self.assertEqual(engine.model_gateway.provider.provider_name, "openai-responses")

    def test_from_environment_without_credentials_stays_honest(self):
        engine = DialogueEngine.from_environment(self.memory, environ={})
        result = engine.respond("Explique la gravité", allow_model=True)
        self.assertFalse(result.used_language_model)
        self.assertEqual(result.model_error, "provider_not_configured")


    def test_dialogue_exposes_injected_mission_service(self):
        class FakeMissionService:
            def create_from_objective(self, objective, *, allow_model=False, max_steps=16):
                return "mission-123", SimpleNamespace(
                    objective=objective, summary="Plan préparé", steps=()
                )

            def run(self, mission_id, *, max_steps=20, max_seconds=300.0):
                return {"mission_id": mission_id, "status": "completed"}

            def approve_step(self, mission_id, step_id):
                return (mission_id, step_id)

            def cancel(self, mission_id):
                return mission_id

        engine = DialogueEngine(self.memory, mission_service=FakeMissionService())
        created = engine.create_mission("Préparer un rapport", allow_model=True)
        self.assertEqual(created["mission_id"], "mission-123")
        self.assertEqual(created["summary"], "Plan préparé")
        self.assertEqual(engine.run_mission("mission-123")["status"], "completed")
        self.assertEqual(engine.approve_mission_step("mission-123", "step-1"), ("mission-123", "step-1"))
        self.assertEqual(engine.cancel_mission("mission-123"), "mission-123")

    def test_dialogue_fails_closed_when_mission_service_missing(self):
        engine = DialogueEngine(self.memory)
        with self.assertRaisesRegex(RuntimeError, "mission_service_not_configured"):
            engine.create_mission("Travailler toute la nuit", allow_model=True)

    def test_without_model_does_not_fake_ai(self):
        result = DialogueEngine(self.memory).respond("Explique la gravité")
        self.assertFalse(result.used_language_model)
        self.assertIn("pas encore connecté", result.reply)
        self.assertEqual(result.model_error, "provider_not_configured")


if __name__ == "__main__":
    unittest.main()
