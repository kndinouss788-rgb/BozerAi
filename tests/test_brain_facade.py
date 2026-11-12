import unittest

from bozer_brain.brain_facade import BozerBrain
from bozer_brain.request_router import RequestRoute


class FakeDialogue:
    def __init__(self):
        self.calls = []

    def respond(self, message, *, allow_model=False, allow_search=False):
        self.calls.append((message, allow_model, allow_search))
        return type("Result", (), {"reply": "Réponse de test"})()


class BrainFacadeTests(unittest.TestCase):
    def setUp(self):
        self.dialogue = FakeDialogue()
        self.brain = BozerBrain(self.dialogue)

    def test_chat_is_delegated_to_dialogue(self):
        result = self.brain.respond("Explique les saisons")
        self.assertEqual(result.decision.route, RequestRoute.CHAT)
        self.assertEqual(result.reply, "Réponse de test")
        self.assertEqual(len(self.dialogue.calls), 1)

    def test_model_and_search_require_explicit_flags(self):
        self.brain.respond("Explique les saisons")
        self.assertEqual(self.dialogue.calls[-1], ("Explique les saisons", False, False))
        self.brain.respond("Recherche les nouveautés Android", allow_search=True)
        self.assertEqual(self.dialogue.calls[-1], ("Recherche les nouveautés Android", False, True))

    def test_action_does_not_call_dialogue(self):
        result = self.brain.respond("Ouvre Settings")
        self.assertEqual(result.decision.route, RequestRoute.DEVICE_ACTION)
        self.assertIsNone(result.dialogue)
        self.assertEqual(self.dialogue.calls, [])

    def test_ambiguous_action_returns_clarification(self):
        result = self.brain.respond("Envoie un message")
        self.assertEqual(result.decision.route, RequestRoute.CLARIFICATION)
        self.assertIsNone(result.dialogue)
        self.assertEqual(self.dialogue.calls, [])

    def test_multi_step_is_planned_without_execution(self):
        result = self.brain.respond("Ouvre Settings puis ouvre Clock")
        self.assertEqual(result.decision.route, RequestRoute.MULTI_STEP)
        self.assertIn("Aucune action n'a encore été exécutée.", result.reply)
        self.assertEqual(self.dialogue.calls, [])


if __name__ == "__main__":
    unittest.main()
