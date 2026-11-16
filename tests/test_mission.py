import os
import tempfile
import threading
import time
import unittest

from bozer_brain.mission import (
    MissionAlreadyRunningError, MissionRunner, MissionStep, MissionStore,
    MissionTool, ToolResult,
)


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

    def test_retryable_error_retries_only_for_explicitly_retry_safe_tool(self):
        attempts = []
        def flaky(_):
            attempts.append(1)
            return ToolResult(False, "temporaire", retryable=True) if len(attempts) == 1 else ToolResult(True, "ok", True)
        mission = self.store.create("Réessayer", (
            MissionStep("a", "Travail", "tool", {}, max_attempts=2),
        ))
        report = MissionRunner(self.store, {
            "tool": MissionTool(flaky, retry_safe=True)
        }).run(mission)
        self.assertEqual(len(attempts), 2)
        self.assertEqual(report.status, "completed")

    def test_unsafe_retry_waits_for_human_approval(self):
        calls = []
        mission = self.store.create("Action non idempotente", (
            MissionStep("a", "Envoyer", "send", {}, max_attempts=2),
        ))
        runner = MissionRunner(self.store, {"send": MissionTool(
            lambda _: calls.append("sent") or ToolResult(False, "état incertain", retryable=True)
        )})
        first = runner.run(mission)
        self.assertEqual(first.status, "paused")
        self.assertEqual(first.waiting_steps, 1)
        self.assertEqual(calls, ["sent"])
        self.store.approve_step(mission, "a")
        second = runner.run(mission)
        self.assertEqual(second.status, "failed")
        self.assertEqual(calls, ["sent", "sent"])

    def test_interrupted_unsafe_step_requires_approval_before_replay(self):
        calls = []
        mission = self.store.create("Reprise prudente", (
            MissionStep("a", "Action externe", "send", {}, max_attempts=2),
        ))
        with self.store._connect() as db:
            db.execute(
                "UPDATE mission_steps SET status='running',attempts=1 WHERE mission_id=? AND step_id='a'",
                (mission,)
            )
            db.execute("UPDATE missions SET status='running' WHERE mission_id=?", (mission,))
            db.execute(
                "INSERT INTO mission_runner_leases(mission_id,owner_token,expires_at) VALUES(?,?,?)",
                (mission, "dead-runner", time.time() - 60)
            )
        runner = MissionRunner(self.store, {
            "send": MissionTool(lambda _: calls.append("sent") or ToolResult(True, "vérifié", True))
        })
        first = runner.run(mission)
        self.assertEqual(first.status, "paused")
        self.assertEqual(first.waiting_steps, 1)
        self.assertEqual(calls, [])
        self.store.approve_step(mission, "a")
        second = runner.run(mission)
        self.assertEqual(second.status, "completed")
        self.assertEqual(calls, ["sent"])

    def test_interrupted_retry_safe_step_resumes_automatically(self):
        calls = []
        mission = self.store.create("Reprise sûre", (
            MissionStep("a", "Lecture", "read", {}, max_attempts=2),
        ))
        with self.store._connect() as db:
            db.execute(
                "UPDATE mission_steps SET status='running',attempts=1 WHERE mission_id=? AND step_id='a'",
                (mission,)
            )
        report = MissionRunner(self.store, {
            "read": MissionTool(lambda _: calls.append("read") or ToolResult(True, "vérifié", True),
                                retry_safe=True)
        }).run(mission)
        self.assertEqual(report.status, "completed")
        self.assertEqual(calls, ["read"])

    def test_second_runner_cannot_execute_same_mission_concurrently(self):
        started = threading.Event()
        release = threading.Event()
        calls = []

        def slow_tool(_):
            calls.append("run")
            started.set()
            self.assertTrue(release.wait(3))
            return ToolResult(True, "ok", True)

        mission = self.store.create("Une seule exécution", (
            MissionStep("a", "Action", "slow", {}),
        ))
        runner = MissionRunner(self.store, {"slow": MissionTool(slow_tool)})
        thread_result = []
        thread = threading.Thread(target=lambda: thread_result.append(runner.run(mission)))
        thread.start()
        self.assertTrue(started.wait(2))
        try:
            with self.assertRaises(MissionAlreadyRunningError):
                runner.run(mission)
        finally:
            release.set()
            thread.join(3)
        self.assertFalse(thread.is_alive())
        self.assertEqual(len(calls), 1)
        self.assertEqual(thread_result[0].status, "completed")

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

    def test_rejects_dependency_cycles_and_missing_dependencies(self):
        with self.assertRaises(ValueError):
            self.store.create("Cycle", (
                MissionStep("a", "A", "x", {}, ("b",)),
                MissionStep("b", "B", "x", {}, ("a",)),
            ))
        with self.assertRaises(ValueError):
            self.store.create("Dépendance absente", (
                MissionStep("a", "A", "x", {}, ("ghost",)),
            ))

    def test_rejects_invalid_arguments_and_attempt_counts(self):
        with self.assertRaises(ValueError):
            self.store.create("Arguments invalides", (
                MissionStep("a", "A", "x", ["not", "object"]),
            ))
        with self.assertRaises(ValueError):
            self.store.create("Essais invalides", (
                MissionStep("a", "A", "x", {}, max_attempts=0),
            ))


if __name__ == "__main__":
    unittest.main()
