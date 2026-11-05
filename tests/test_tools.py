import unittest

from bozer_brain.models import ActionProposal
from bozer_brain.tools import ToolDefinition, ToolRegistry


def proposal(action, arguments, confirmation=False, safe=True, permission=None):
    return ActionProposal(
        action=action,
        arguments=arguments,
        requires_confirmation=confirmation,
        required_permission=permission,
        safe_to_execute=safe,
        explanation="test",
    )


class ToolRegistryTests(unittest.TestCase):
    def setUp(self):
        self.registry = ToolRegistry.android_defaults()

    def test_unknown_tool_is_rejected(self):
        decision = self.registry.authorize(proposal("delete_everything", {}))
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.reason, "tool_not_registered")

    def test_unsafe_proposal_is_rejected(self):
        decision = self.registry.authorize(
            proposal("open_app", {"target": "Settings"}, safe=False)
        )
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.reason, "proposal_not_actionable")

    def test_required_argument_is_checked(self):
        decision = self.registry.authorize(proposal("open_app", {}))
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.reason, "missing_required_arguments")

    def test_permission_is_required(self):
        decision = self.registry.authorize(
            proposal("set_alarm", {"time": "07:00"})
        )
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.reason, "permission_not_granted")

    def test_sensitive_action_requires_confirmation(self):
        decision = self.registry.authorize(
            proposal("make_call", {"target": "Maman"}, permission="CALL_PHONE"),
            granted_permissions=frozenset({"CALL_PHONE"}),
        )
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.reason, "confirmation_required")

    def test_sensitive_action_authorized_only_after_both_gates(self):
        decision = self.registry.authorize(
            proposal("make_call", {"target": "Maman"}, confirmation=True),
            granted_permissions=frozenset({"CALL_PHONE"}),
            user_confirmed=True,
        )
        self.assertTrue(decision.allowed)
        self.assertEqual(decision.reason, "authorized_for_adapter")

    def test_duplicate_tool_registration_is_rejected(self):
        registry = ToolRegistry()
        item = ToolDefinition("sample", frozenset())
        registry.register(item)
        with self.assertRaises(ValueError):
            registry.register(item)


if __name__ == "__main__":
    unittest.main()
