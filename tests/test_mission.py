import os
import tempfile
import unittest

from bozer_brain.mission import MissionRunner, MissionStep, MissionStore, MissionTool, ToolResult


class MissionRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = MissionStore(os.path.join(self.temp.name, "missions.db"))

    def tearDown(self):
        self.temp.cleanup()

    def test_runs_steps_in_dependency_order_and_verifies_results(self):
        calls = []
        tools = {"work": MissionTool(lambda args: calls.append(args["n"]) or ToolResult(True, "ok", True))}
        mission = self.store.create("Faire deux tâches", (
            MissionStep("b", "Deux", "work", {"n": 2}, ("a",)),
            MissionStep("a", "Un", "work", {"n": 1}),
        ))
        report = MissionRunner(self.store, tools).run(mission)
        self.assertEqual(calls, [1, 2])
        self.assertEqual(report.status, "completed")
        self.assertEqual(report.completed_steps, 2)

    def test_unverified_success_is_not_marked_complete(self):
        mission = self.store.create("Vérifier", (MissionStep("a", "Action", "work", {}),))
        report = MissionRunner(self.store, {"work": MissionTool(
            lambda _: ToolResult(True, "pas de preuve", False)
        )}).run(mission)
        self.assertEqual(report.status, "failed")
        self.assertEqual(report.completed_steps, 0)

    def test_confirmation_pauses_then_resumes_after_approval(self):
        calls = []
        mission = self.store.create("Action sensible", (
            MissionStep("a", "Envoyer", "send", {}, requires_confirmation=True),
        ))
        runner = MissionRunner(self.store, {"send": MissionTool(
            lambda _: calls.append("sent") or ToolResult(True, "confirmé", True)
        )})
        first = runner.run(mission)
        self.assertEqual(first.status, "paused")
        self.assertEqual(calls, [])
        self.store.approve_step(mission, "a")
        second = runner.run(mission)
        self.assertEqual(second.status, "completed")
        self.assertEqual(calls, ["sent"])

    def test_missing_permission_blocks_tool(self):
        mission = self.store.create("Action protégée", (MissionStep("a", "Ouvrir", "tool", {}),))
        report = MissionRunner(self.store, {"tool": MissionTool(
            lambda _: ToolResult(True, "ok", True), required_permission="CAMERA"
        )}).run(mission)
        self.assertEqual(report.status, "failed")
        self.assertEqual(report.failed_steps, 1)

    def test_retryable_error_retries_and_checkpoints(self):
        attempts = []
        def flaky(_):
            attempts.append(1)
            return ToolResult(False, "temporaire", retryable=True) if len(attempts) == 1 else ToolResult(True, "ok", True)
        mission = self.store.create("Réessayer", (MissionStep("a", "Travail", "tool", {}, max_attempts=2),))
        report = MissionRunner(self.store, {"tool": MissionTool(flaky)}).run(mission)
        self.assertEqual(len(attempts), 2)
        self.assertEqual(report.status, "completed")

    def test_limit_leaves_mission_resumable(self):
        calls = []
        mission = self.store.create("Trois étapes", tuple(
            MissionStep(str(i), "Étape", "tool", {"i": i}) for i in range(3)
        ))
        runner = MissionRunner(self.store, {"tool": MissionTool(
            lambda a: calls.append(a["i"]) or ToolResult(True, "ok", True)
        )})
        first = runner.run(mission, max_steps=1)
        self.assertEqual(first.status, "limit_reached")
        second = runner.run(mission, max_steps=5)
        self.assertEqual(second.status, "completed")
        self.assertEqual(calls, [0, 1, 2])

    def test_rejects_dependency_cycles(self):
        with self.assertRaises(ValueError):
            self.store.create("Cycle", (
                MissionStep("a", "A", "x", {}, ("b",)),
                MissionStep("b", "B", "x", {}, ("a",)),
            ))


if __name__ == "__main__":
    unittest.main()
