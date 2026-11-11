import json
import os
import tempfile
import unittest
from types import SimpleNamespace

from bozer_brain.mission import MissionStore, MissionTool, ToolResult
from bozer_brain.mission_planner import MissionPlanner, MissionPlanningError, MissionService


class FakeGateway:
    def __init__(self, payload):
        self.payload = payload
        self.calls = []

    def generate(self, prompt, *, enabled=False, max_output_chars=6000):
        self.calls.append((prompt, enabled, max_output_chars))
        return SimpleNamespace(ok=True, text=self.payload, error=None)


class MissionPlannerTests(unittest.TestCase):
    def setUp(self):
        self.catalog = {
            "research": {"description": "Collect public project information"},
            "write_report": {"description": "Write a report"},
            "send_message": {"description": "Prepare a message"},
        }

    def test_creates_dependency_ordered_plan_using_only_catalog_tools(self):
        gateway = FakeGateway(json.dumps({
            "summary": "Research then report",
            "steps": [
                {"step_id": "report", "title": "Write", "tool": "write_report",
                 "arguments": {}, "depends_on": ["research"], "max_attempts": 2},
                {"step_id": "research", "title": "Research", "tool": "research",
                 "arguments": {"query": "BOZER"}, "depends_on": [], "max_attempts": 1},
            ],
        }))
        plan = MissionPlanner(gateway).plan("Prepare a BOZER report", self.catalog, enabled=True)
        self.assertEqual(len(plan.steps), 2)
        self.assertEqual(plan.steps[0].step_id, "report")
        self.assertEqual(gateway.calls[0][1], True)

    def test_sensitive_tools_are_forced_to_require_confirmation(self):
        gateway = FakeGateway(json.dumps({
            "summary": "Post", "steps": [
                {"step_id": "post", "title": "Publish", "tool": "send_message",
                 "arguments": {"text": "hello"}, "depends_on": []},
            ],
        }))
        plan = MissionPlanner(gateway).plan("Send this", self.catalog, enabled=True)
        self.assertTrue(plan.steps[0].requires_confirmation)

    def test_unknown_tool_is_rejected(self):
        gateway = FakeGateway(json.dumps({
            "summary": "bad", "steps": [
                {"step_id": "x", "title": "Do", "tool": "shell",
                 "arguments": {}, "depends_on": []},
            ],
        }))
        with self.assertRaises(MissionPlanningError):
            MissionPlanner(gateway).plan("Do it", self.catalog, enabled=True)

    def test_invalid_json_and_cycles_are_rejected(self):
        with self.assertRaises(MissionPlanningError):
            MissionPlanner(FakeGateway("not json")).plan("Do it", self.catalog, enabled=True)
        cycle = FakeGateway(json.dumps({"summary": "cycle", "steps": [
            {"step_id": "a", "title": "A", "tool": "research", "depends_on": ["b"]},
            {"step_id": "b", "title": "B", "tool": "write_report", "depends_on": ["a"]},
        ]}))
        with self.assertRaises(MissionPlanningError):
            MissionPlanner(cycle).plan("Do it", self.catalog, enabled=True)

    def test_model_must_be_explicitly_enabled(self):
        with self.assertRaises(MissionPlanningError):
            MissionPlanner(FakeGateway("{}")).plan("Do it", self.catalog)

    def test_service_creates_and_executes_registered_mission_tools(self):
        with tempfile.TemporaryDirectory() as directory:
            store = MissionStore(os.path.join(directory, "missions.sqlite3"))
            gateway = FakeGateway(json.dumps({"summary": "Research complete", "steps": [
                {"step_id": "research", "title": "Research", "tool": "research",
                 "arguments": {"topic": "BOZER"}, "depends_on": []},
            ]}))
            calls = []
            service = MissionService(
                store, gateway,
                {"research": MissionTool(lambda args: calls.append(args["topic"]) or ToolResult(True, "verified", True))},
                {"research": {"description": "Research public sources"}},
            )
            mission_id, plan = service.create_from_objective("Research BOZER", allow_model=True)
            report = service.run(mission_id)
            self.assertEqual(plan.summary, "Research complete")
            self.assertEqual(calls, ["BOZER"])
            self.assertEqual(report.status, "completed")

    def test_tool_handler_and_catalog_must_match(self):
        with tempfile.TemporaryDirectory() as directory:
            store = MissionStore(os.path.join(directory, "missions.sqlite3"))
            with self.assertRaises(ValueError):
                MissionService(store, FakeGateway("{}"), {}, self.catalog)


if __name__ == "__main__":
    unittest.main()
