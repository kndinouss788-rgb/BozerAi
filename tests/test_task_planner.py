import unittest

from bozer_brain.models import ActionProposal
from bozer_brain.task_planner import TaskPlanner, TaskStep, single_step
from bozer_brain.tools import ToolRegistry


def proposal(action, args, confirmation=False, safe=True):
    return ActionProposal(action, args, confirmation, None, safe, "test proposal")


class TaskPlannerTests(unittest.TestCase):
    def setUp(self):
        self.planner = TaskPlanner(ToolRegistry.android_defaults())

    def test_orders_steps_by_dependencies_not_input_order(self):
        steps = (
            TaskStep("second", proposal("open_app", {"target": "Settings"}), ("first",)),
            TaskStep("first", proposal("open_app", {"target": "Clock"})),
        )
        plan = self.planner.build_plan(steps)
        self.assertTrue(plan.valid)
        self.assertEqual([s.step_id for s in plan.steps], ["first", "second"])
        self.assertEqual(len(plan.ready_steps), 2)

    def test_unknown_dependency_invalidates_plan(self):
        plan = self.planner.build_plan((
            TaskStep("a", proposal("open_app", {"target": "Clock"}), ("missing",)),
        ))
        self.assertFalse(plan.valid)
        self.assertIn("unknown_dependency:a:missing", plan.errors)

    def test_cycle_invalidates_plan(self):
        plan = self.planner.build_plan((
            TaskStep("a", proposal("open_app", {"target": "Clock"}), ("b",)),
            TaskStep("b", proposal("open_app", {"target": "Settings"}), ("a",)),
        ))
        self.assertFalse(plan.valid)
        self.assertEqual(plan.errors, ("dependency_cycle",))

    def test_duplicate_ids_invalidates_plan(self):
        plan = self.planner.build_plan((
            TaskStep("a", proposal("open_app", {"target": "Clock"})),
            TaskStep("a", proposal("open_app", {"target": "Settings"})),
        ))
        self.assertFalse(plan.valid)

    def test_missing_permission_blocks_step(self):
        plan = self.planner.build_plan((
            TaskStep("alarm", proposal("set_alarm", {"time": "07:00"})),
        ))
        self.assertEqual(plan.steps[0].reason, "permission_not_granted")

    def test_unconfirmed_sensitive_step_blocks_dependents(self):
        plan = self.planner.build_plan((
            TaskStep("call", proposal("make_call", {"target": "Maman"}, True)),
            TaskStep("open", proposal("open_app", {"target": "Clock"}), ("call",)),
        ), granted_permissions=frozenset({"CALL_PHONE"}))
        self.assertEqual(plan.steps[0].reason, "confirmation_required")
        self.assertEqual(plan.steps[1].reason, "dependency_not_ready:call")

    def test_confirmation_and_permission_make_sensitive_step_ready(self):
        plan = self.planner.build_plan(
            (TaskStep("call", proposal("make_call", {"target": "Maman"}, True)),),
            granted_permissions=frozenset({"CALL_PHONE"}),
            confirmed_step_ids=frozenset({"call"}),
        )
        self.assertEqual(len(plan.ready_steps), 1)

    def test_message_adapter_creates_single_proposal(self):
        step = single_step("ouvre Settings")
        self.assertEqual(step.step_id, "step-1")
        self.assertEqual(step.proposal.action, "open_app")

    def test_unknown_tool_is_blocked_not_executed(self):
        plan = self.planner.build_plan((
            TaskStep("unknown", proposal("erase_phone", {})),
        ))
        self.assertEqual(plan.steps[0].reason, "tool_not_registered")


if __name__ == "__main__":
    unittest.main()
