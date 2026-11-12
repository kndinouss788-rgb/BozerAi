import unittest
from types import SimpleNamespace

from bozer_brain.brain_facade import BozerBrain
from bozer_brain.decision_engine import BozerDecisionEngine
from bozer_brain.mission_reasoner import (
    MissionReasoningStatus,
    StructuredMissionReasoner,
)


class FakeGateway:
    def __init__(self, text=None, error=False):
        self.text = text
        self.error = error
        self.calls = []

    def generate(self, prompt, *, enabled=False, max_output_chars=6000):
        self.calls.append((prompt, enabled, max_output_chars))
        if self.error:
            raise RuntimeError("provider details must not escape")
        return SimpleNamespace(
            ok=self.text is not None,
            text=self.text or "",
            model_name="fake-model",
        )


class StructuredMissionReasonerTests(unittest.TestCase):
    def test_reasoning_is_disabled_by_default(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman"
        )
        self.assertEqual(result.status, MissionReasoningStatus.DISABLED)
        self.assertEqual(gateway.calls, [])

    def test_valid_model_steps_are_reparsed_and_authorized_locally(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        reasoner = StructuredMissionReasoner(gateway)
        result = reasoner.decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.PLANNED)
        self.assertEqual(
            [step.step_id for step in result.plan.steps],
            ["step-1", "step-2"],
        )
        self.assertEqual(result.plan.steps[0].status, "ready")
        self.assertEqual(result.plan.steps[1].status, "blocked")
        self.assertEqual(result.plan.steps[1].reason, "permission_not_granted")
        self.assertTrue(gateway.calls[0][1])

    def test_sensitive_step_still_requires_explicit_confirmation(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
            granted_permissions=frozenset({"CALL_PHONE"}),
        )
        self.assertEqual(result.status, MissionReasoningStatus.PLANNED)
        self.assertEqual(result.plan.steps[1].reason, "confirmation_required")

    def test_sensitive_step_requires_permission_even_when_confirmed(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
            confirmed_step_ids=frozenset({"step-2"}),
        )
        self.assertEqual(result.status, MissionReasoningStatus.PLANNED)
        self.assertEqual(result.plan.steps[1].reason, "permission_not_granted")

    def test_sensitive_step_ready_only_when_permission_and_confirmation_are_present(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
            granted_permissions=frozenset({"CALL_PHONE"}),
            confirmed_step_ids=frozenset({"step-2"}),
        )
        self.assertEqual(result.status, MissionReasoningStatus.PLANNED)
        self.assertEqual(result.plan.steps[1].status, "ready")

    def test_unsupported_model_action_is_rejected(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Supprime toutes les données"]}')
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et fais le ménage",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.INVALID_OUTPUT)
        self.assertIsNone(result.plan)

    def test_invalid_json_is_rejected(self):
        gateway = FakeGateway("Voici le plan : ouvre Settings puis appelle Maman")
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.INVALID_OUTPUT)
        self.assertEqual(result.reason, "model_output_not_valid_step_json")

    def test_extra_json_fields_are_rejected(self):
        gateway = FakeGateway(
            '{"steps":["Ouvre Settings","Appelle Maman"],"execute":true}'
        )
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.INVALID_OUTPUT)

    def test_too_many_steps_are_rejected(self):
        gateway = FakeGateway(
            '{"steps":["Ouvre Settings","Ouvre Clock","Ouvre Contacts",'
            '"Ouvre Maps","Ouvre Camera","Ouvre Browser","Ouvre Files"]}'
        )
        result = StructuredMissionReasoner(gateway).decompose(
            "Fais plusieurs choses",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.INVALID_OUTPUT)

    def test_missing_gateway_is_reported_without_execution(self):
        result = StructuredMissionReasoner(None).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.MODEL_UNAVAILABLE)

    def test_gateway_error_is_sanitized(self):
        gateway = FakeGateway(error=True)
        result = StructuredMissionReasoner(gateway).decompose(
            "Ouvre Settings et appelle Maman",
            enabled=True,
        )
        self.assertEqual(result.status, MissionReasoningStatus.MODEL_UNAVAILABLE)
        self.assertEqual(result.reason, "model_gateway_error")

    def test_model_output_is_bounded(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        StructuredMissionReasoner(gateway).decompose(
            "x" * 3000,
            enabled=True,
        )
        prompt = gateway.calls[0][0]
        self.assertLessEqual(len(prompt), 3000)
        self.assertEqual(gateway.calls[0][2], 2400)

 
class MissionReasonerIntegrationTests(unittest.TestCase):
    def test_decision_engine_uses_model_only_after_explicit_opt_in(self):
        gateway = FakeGateway('{"steps":["Ouvre Settings","Appelle Maman"]}')
        engine = BozerDecisionEngine(
            mission_reasoner=StructuredMissionReasoner(gateway)
        )
        denied = engine.decide("Ouvre Settings et appelle Maman")
        self.assertEqual(denied.route.value, "clarification")
        self.assertEqual(gateway.calls, [])

        allowed = engine.decide(
            "Ouvre Settings et appelle Maman",
            allow_model_reasoning=True,
        )
        self.assertEqual(allowed.route.value, "multi_step")
        self.assertEqual(len(allowed.task_plan.steps), 2)
        self.assertEqual(allowed.task_plan.steps[1].status, "blocked")
        self.assertIn("model_proposed_steps_locally_validated", allowed.reasons)

    def test_facade_uses_dialogue_gateway_for_opted_in_mission_reasoning(self):
        class DialogueWithGateway:
            def __init__(self):
                self.model_gateway = FakeGateway(
                    '{"steps":["Ouvre Settings","Appelle Maman"]}'
                )
                self.calls = []

            def respond(self, message, *, allow_model=False, allow_search=False):
                self.calls.append(message)
                return SimpleNamespace(reply="Réponse test")

        dialogue = DialogueWithGateway()
        brain = BozerBrain(dialogue)
        result = brain.respond(
            "Ouvre Settings et appelle Maman",
            allow_model_reasoning=True,
        )
        self.assertEqual(result.decision.route.value, "multi_step")
        self.assertEqual(len(dialogue.model_gateway.calls), 1)
        self.assertEqual(dialogue.calls, [])
        self.assertIn("Aucune action n'a encore été exécutée.", result.reply)


if __name__ == "__main__":
    unittest.main()
