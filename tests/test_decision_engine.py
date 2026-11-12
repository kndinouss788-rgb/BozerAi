import unittest

from bozer_brain.decision_engine import BozerDecisionEngine
from bozer_brain.request_router import RequestRoute
from bozer_brain.verification import VerificationStatus, verify_outcome


class DecisionEngineTests(unittest.TestCase):
    def setUp(self):
        self.engine = BozerDecisionEngine()

    def test_chat_has_no_action_plan(self):
        decision = self.engine.decide("Explique-moi les éclipses")
        self.assertEqual(decision.route, RequestRoute.CHAT)
        self.assertIsNone(decision.task_plan)

    def test_single_action_is_planned_but_not_executed(self):
        decision = self.engine.decide("Ouvre Settings")
        self.assertEqual(decision.route, RequestRoute.DEVICE_ACTION)
        self.assertIsNotNone(decision.task_plan)
        self.assertEqual(len(decision.task_plan.ready_steps), 1)
        self.assertIn("planning_only_no_execution", decision.reasons)

    def test_multi_step_mission_is_decomposed_in_order(self):
        decision = self.engine.decide("Ouvre Settings puis ouvre Clock")
        self.assertEqual(decision.route, RequestRoute.MULTI_STEP)
        self.assertEqual(
            [step.step_id for step in decision.task_plan.steps],
            ["step-1", "step-2"],
        )
        self.assertEqual(decision.task_plan.steps[1].depends_on, ("step-1",))

    def test_sensitive_action_stays_blocked_without_permission_and_confirmation(self):
        decision = self.engine.decide("Appelle Maman")
        self.assertEqual(len(decision.task_plan.ready_steps), 0)
        self.assertEqual(decision.task_plan.steps[0].reason, "permission_not_granted")

    def test_sensitive_action_requires_confirmation_even_with_permission(self):
        decision = self.engine.decide(
            "Appelle Maman",
            granted_permissions=frozenset({"CALL_PHONE"}),
        )
        self.assertEqual(len(decision.task_plan.ready_steps), 0)
        self.assertEqual(decision.task_plan.steps[0].reason, "confirmation_required")

    def test_sensitive_action_is_ready_only_after_permission_and_confirmation(self):
        decision = self.engine.decide(
            "Appelle Maman",
            granted_permissions=frozenset({"CALL_PHONE"}),
            confirmed_step_ids=frozenset({"step-1"}),
        )
        self.assertEqual(len(decision.task_plan.ready_steps), 1)

    def test_blocked_step_prevents_dependent_step_from_becoming_ready(self):
        decision = self.engine.decide(
            "Ouvre Settings puis appelle Maman puis ouvre Clock"
        )
        self.assertEqual(decision.route, RequestRoute.MULTI_STEP)
        self.assertEqual(decision.task_plan.steps[0].status, "ready")
        self.assertEqual(decision.task_plan.steps[1].status, "blocked")
        self.assertTrue(decision.task_plan.steps[2].status == "blocked")
        self.assertEqual(
            decision.task_plan.steps[2].reason,
            "dependency_not_ready:step-2",
        )

    def test_unknown_multi_step_is_not_guessed(self):
        decision = self.engine.decide("Ouvre Settings puis supprime toutes les données")
        self.assertEqual(decision.route, RequestRoute.CLARIFICATION)
        self.assertIsNone(decision.task_plan)

    def test_unsupported_multi_action_separator_returns_clarification(self):
        decision = self.engine.decide("Ouvre Settings et appelle Maman")
        self.assertEqual(decision.route, RequestRoute.CLARIFICATION)
        self.assertIsNone(decision.task_plan)


class OutcomeVerificationTests(unittest.TestCase):
    def test_exact_observed_state_is_verified(self):
        result = verify_outcome("alarm scheduled", "alarm scheduled", True)
        self.assertEqual(result.status, VerificationStatus.VERIFIED)

    def test_missing_observation_is_unverified(self):
        result = verify_outcome("message sent", None, True)
        self.assertEqual(result.status, VerificationStatus.UNVERIFIED)

    def test_mismatch_is_failed(self):
        result = verify_outcome("app opened", "app not found", True)
        self.assertEqual(result.status, VerificationStatus.FAILED)

    def test_adapter_failure_is_failed(self):
        result = verify_outcome("call started", None, False)
        self.assertEqual(result.status, VerificationStatus.FAILED)


if __name__ == "__main__":
    unittest.main()
