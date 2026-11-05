import unittest

from bozer_brain import parse_intent, plan_action
from bozer_brain.models import IntentKind


class IntentParserTests(unittest.TestCase):
    def test_alarm(self):
        intent = parse_intent("Mets une alarme à 6:30")
        self.assertEqual(intent.kind, IntentKind.SET_ALARM)
        self.assertEqual(intent.time_text, "6:30")
        self.assertTrue(plan_action(intent).safe_to_execute)

    def test_open_app(self):
        intent = parse_intent("Ouvre WhatsApp")
        self.assertEqual(intent.kind, IntentKind.OPEN_APP)
        self.assertEqual(intent.target, "WhatsApp")
        self.assertIsNone(plan_action(intent).required_permission)

    def test_message_requires_confirmation(self):
        proposal = plan_action(parse_intent("Envoie un message à maman"))
        self.assertEqual(proposal.action, "send_message")
        self.assertTrue(proposal.requires_confirmation)
        self.assertEqual(proposal.required_permission, "SEND_SMS_OR_MESSAGING_APP")

    def test_call_requires_confirmation(self):
        proposal = plan_action(parse_intent("Appelle le contact Ali"))
        self.assertEqual(proposal.action, "make_call")
        self.assertTrue(proposal.requires_confirmation)

    def test_unknown_request_stays_chat(self):
        intent = parse_intent("Explique-moi la gravité")
        proposal = plan_action(intent)
        self.assertEqual(intent.kind, IntentKind.CHAT)
        self.assertFalse(proposal.safe_to_execute)
        self.assertEqual(proposal.action, "chat")

    def test_empty_input_fails_closed(self):
        proposal = plan_action(parse_intent(""))
        self.assertFalse(proposal.safe_to_execute)


if __name__ == "__main__":
    unittest.main()
