import unittest

from bozer_brain.request_router import RequestRoute, analyze_request


class RequestRouterTests(unittest.TestCase):
    def test_empty_request_is_rejected(self):
        with self.assertRaises(ValueError):
            analyze_request("  ")

    def test_general_question_routes_to_chat(self):
        result = analyze_request("Pourquoi le ciel est bleu ?")
        self.assertEqual(result.route, RequestRoute.CHAT)
        self.assertFalse(result.action_proposal.safe_to_execute)

    def test_explicit_supported_action_routes_to_device(self):
        result = analyze_request("ouvre Settings")
        self.assertEqual(result.route, RequestRoute.DEVICE_ACTION)
        self.assertEqual(result.action_proposal.action, "open_app")

    def test_research_request_is_not_executed(self):
        result = analyze_request("Recherche les dernières informations sur Android")
        self.assertEqual(result.route, RequestRoute.RESEARCH)
        self.assertIn("external_search_requires_explicit_opt_in", result.reasons)

    def test_incomplete_action_requests_clarification(self):
        result = analyze_request("Envoie un message")
        self.assertEqual(result.route, RequestRoute.CLARIFICATION)
        self.assertTrue(result.requires_user_input)

    def test_multi_action_request_is_detected_without_execution(self):
        result = analyze_request("Ouvre Settings puis ouvre Clock")
        self.assertEqual(result.route, RequestRoute.MULTI_STEP)
        self.assertIn("planning_only_no_execution", result.reasons)


if __name__ == "__main__":
    unittest.main()
