"""Persistent, bounded and guarded mission runner for BOZER ACTION.

A trusted host supplies the planner and registered tool handlers. The model
can propose a plan, but only the host can register tools and grant permissions.
SQLite checkpoints missions, serializes runners per mission, and requires a
fresh human approval before replaying an interrupted non-idempotent action.
"""
import json
import math
import sqlite3
import threading
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, Iterable, Optional, Tuple


TERMINAL_MISSION_STATES = {"completed", "failed", "cancelled"}
VALID_STEP_STATES = {
    "pending", "running", "succeeded", "failed", "waiting_approval", "blocked"
}


@dataclass(frozen=True)
class MissionStep:
    step_id: str
    title: str
    tool: str
    arguments: dict
    depends_on: Tuple[str, ...] = ()
    requires_confirmation: bool = False
    max_attempts: int = 2


@dataclass(frozen=True)
class ToolResult:
    success: bool
    summary: str
    verified: bool = False
    retryable: bool = False

    def __post_init__(self):
        if not isinstance(self.success, bool):
            raise TypeError("ToolResult.success must be bool")
        if not isinstance(self.summary, str):
            raise TypeError("ToolResult.summary must be str")
        if not isinstance(self.verified, bool) or not isinstance(self.retryable, bool):
            raise TypeError("ToolResult flags must be bool")


@dataclass(frozen=True)
class MissionReport:
    mission_id: str
    objective: str
    status: str
    completed_steps: int
    total_steps: int
    failed_steps: int
    waiting_steps: int
    events: Tuple[str, ...]


@dataclass(frozen=True)
class MissionTool:
    handler: Callable[[dict], ToolResult]
    required_permission: Optional[str] = None
    requires_confirmation: bool = False
    # True only when repeating the same handler call cannot duplicate its effect.
    retry_safe: bool = False

    def __post_init__(self):
        if not callable(self.handler):
            raise TypeError("MissionTool.handler must be callable")
        if self.required_permission is not None and (
            not isinstance(self.required_permission, str) or not self.required_permission.strip()
        ):
            raise ValueError("required_permission must be a non-empty string or None")
        if not isinstance(self.requires_confirmation, bool) or not isinstance(self.retry_safe, bool):
            raise TypeError("MissionTool flags must be bool")


class MissionAlreadyRunningError(RuntimeError):
    """Raised when another live runner already owns this mission."""


class MissionStore:
    """SQLite-backed mission state with a renewable, per-mission runner lease."""

    def __init__(self, database_path: str):
        if not isinstance(database_path, str) or not database_path.strip() or database_path == ":memory:":
            raise ValueError("database_path must be a persistent SQLite file path")
        self.database_path = str(database_path)
        Path(self.database_path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS missions (
                mission_id TEXT PRIMARY KEY, objective TEXT NOT NULL,
                status TEXT NOT NULL, created_at REAL NOT NULL,
                updated_at REAL NOT NULL, cancel_requested INTEGER NOT NULL DEFAULT 0
            )""")
            db.execute("""CREATE TABLE IF NOT EXISTS mission_steps (
                mission_id TEXT NOT NULL, step_id TEXT NOT NULL, title TEXT NOT NULL,
                tool TEXT NOT NULL, arguments TEXT NOT NULL, depends_on TEXT NOT NULL,
                requires_confirmation INTEGER NOT NULL, approved INTEGER NOT NULL DEFAULT 0,
                max_attempts INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL, last_result TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(mission_id, step_id),
                FOREIGN KEY(mission_id) REFERENCES missions(mission_id)
            )""")
            db.execute("""CREATE TABLE IF NOT EXISTS mission_events (
                event_id INTEGER PRIMARY KEY AUTOINCREMENT, mission_id TEXT NOT NULL,
                created_at REAL NOT NULL, message TEXT NOT NULL,
                FOREIGN KEY(mission_id) REFERENCES missions(mission_id)
            )""")
            db.execute("""CREATE TABLE IF NOT EXISTS mission_runner_leases (
                mission_id TEXT PRIMARY KEY, owner_token TEXT NOT NULL,
                expires_at REAL NOT NULL,
                FOREIGN KEY(mission_id) REFERENCES missions(mission_id)
            )""")

    def _connect(self):
        db = sqlite3.connect(self.database_path, timeout=15)
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=15000")
        return db

    def create(self, objective: str, steps: Iterable[MissionStep]) -> str:
        if not isinstance(objective, str) or not objective.strip():
            raise ValueError("objective and at least one step are required")
        objective = objective.strip()
        items = tuple(steps)
        if not items or len(items) > 32:
            raise ValueError("a mission must contain between 1 and 32 steps")
        if any(not isinstance(step, MissionStep) for step in items):
            raise ValueError("every mission step must be a MissionStep")
        ids = [s.step_id for s in items]
        if any(not isinstance(x, str) or not x.strip() or len(x) > 80 for x in ids):
            raise ValueError("step IDs must be non-empty strings of at most 80 characters")
        if len(ids) != len(set(ids)):
            raise ValueError("step IDs must be unique")
        known = set(ids)
        for step in items:
            if not isinstance(step.title, str) or not step.title.strip() or len(step.title) > 180:
                raise ValueError("each step needs a title of at most 180 characters")
            if not isinstance(step.tool, str) or not step.tool.strip() or len(step.tool) > 80:
                raise ValueError("each step needs a valid tool name")
            if not isinstance(step.arguments, dict):
                raise ValueError("step arguments must be a JSON object")
            if not isinstance(step.depends_on, (tuple, list)):
                raise ValueError("step dependencies must be a list or tuple")
            if not all(isinstance(dep, str) and dep.strip() for dep in step.depends_on):
                raise ValueError("step dependency IDs must be non-empty strings")
            if len(step.depends_on) != len(set(step.depends_on)):
                raise ValueError("duplicate step dependencies are not allowed")
            if any(dep not in known or dep == step.step_id for dep in step.depends_on):
                raise ValueError("step dependency is missing or self-referential")
            if not isinstance(step.max_attempts, int) or isinstance(step.max_attempts, bool) or not 1 <= step.max_attempts <= 3:
                raise ValueError("max_attempts must be an integer between 1 and 3")
            if not isinstance(step.requires_confirmation, bool):
                raise ValueError("requires_confirmation must be bool")
            try:
                serialized = json.dumps(step.arguments, ensure_ascii=False, allow_nan=False)
            except (TypeError, ValueError) as exc:
                raise ValueError("step arguments must contain finite JSON values only") from exc
            if len(serialized.encode("utf-8")) > 65536:
                raise ValueError("step arguments exceed the 64 KiB safety limit")
        self._assert_acyclic(items)
        mission_id = str(uuid.uuid4())
        now = time.time()
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute(
                "INSERT INTO missions(mission_id,objective,status,created_at,updated_at) VALUES(?,?,?,?,?)",
                (mission_id, objective, "queued", now, now),
            )
            for step in items:
                db.execute("""INSERT INTO mission_steps(
                    mission_id,step_id,title,tool,arguments,depends_on,
                    requires_confirmation,max_attempts,status
                ) VALUES(?,?,?,?,?,?,?,?,?)""",
                    (mission_id, step.step_id, step.title, step.tool,
                     json.dumps(step.arguments, ensure_ascii=False, allow_nan=False),
                     json.dumps(list(step.depends_on), ensure_ascii=False),
                     int(step.requires_confirmation), step.max_attempts, "pending"))
            self._event(db, mission_id, "Mission créée")
        return mission_id

    @staticmethod
    def _assert_acyclic(steps):
        done, pending = set(), list(steps)
        while pending:
            ready = next((s for s in pending if set(s.depends_on).issubset(done)), None)
            if ready is None:
                raise ValueError("mission dependency graph contains a cycle or missing dependency")
            done.add(ready.step_id)
            pending.remove(ready)

    @staticmethod
    def _event(db, mission_id, message):
        db.execute(
            "INSERT INTO mission_events(mission_id,created_at,message) VALUES(?,?,?)",
            (mission_id, time.time(), str(message)[:1000])
        )

    def request_cancel(self, mission_id: str):
        with self._connect() as db:
            row = db.execute("SELECT status FROM missions WHERE mission_id=?", (mission_id,)).fetchone()
            if not row:
                raise KeyError(mission_id)
            if row[0] in TERMINAL_MISSION_STATES:
                raise ValueError("a terminal mission cannot be cancelled")
            db.execute(
                "UPDATE missions SET cancel_requested=1,updated_at=? WHERE mission_id=?",
                (time.time(), mission_id)
            )
            self._event(db, mission_id, "Annulation demandée")

    def approve_step(self, mission_id: str, step_id: str):
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            mission = db.execute("SELECT status FROM missions WHERE mission_id=?", (mission_id,)).fetchone()
            if not mission:
                raise KeyError(mission_id)
            if mission[0] in TERMINAL_MISSION_STATES:
                raise ValueError("a terminal mission cannot be approved")
            cur = db.execute("""UPDATE mission_steps SET approved=1,status='pending'
                WHERE mission_id=? AND step_id=? AND status='waiting_approval'""",
                (mission_id, step_id))
            if not cur.rowcount:
                raise ValueError("step is not waiting for approval")
            db.execute(
                "UPDATE missions SET status='queued',updated_at=? WHERE mission_id=?",
                (time.time(), mission_id)
            )
            self._event(db, mission_id, "Autorisation accordée pour " + step_id)

    def acquire_runner_lease(self, mission_id: str, owner_token: str, lease_seconds: float) -> bool:
        now = time.time()
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            if not db.execute("SELECT 1 FROM missions WHERE mission_id=?", (mission_id,)).fetchone():
                raise KeyError(mission_id)
            current = db.execute(
                "SELECT owner_token,expires_at FROM mission_runner_leases WHERE mission_id=?",
                (mission_id,)
            ).fetchone()
            if current and current[1] > now and current[0] != owner_token:
                return False
            db.execute("""INSERT INTO mission_runner_leases(mission_id,owner_token,expires_at)
                VALUES(?,?,?) ON CONFLICT(mission_id) DO UPDATE SET
                owner_token=excluded.owner_token,expires_at=excluded.expires_at""",
                (mission_id, owner_token, now + lease_seconds))
            return True

    def renew_runner_lease(self, mission_id: str, owner_token: str, lease_seconds: float) -> bool:
        with self._connect() as db:
            cur = db.execute("""UPDATE mission_runner_leases SET expires_at=?
                WHERE mission_id=? AND owner_token=?""",
                (time.time() + lease_seconds, mission_id, owner_token))
            return cur.rowcount == 1

    def release_runner_lease(self, mission_id: str, owner_token: str):
        with self._connect() as db:
            db.execute(
                "DELETE FROM mission_runner_leases WHERE mission_id=? AND owner_token=?",
                (mission_id, owner_token)
            )

    def recover_interrupted_steps(self, mission_id: str, retry_safe_tools):
        """Reconcile steps left running after an expired runner lease."""
        safe_tools = set(retry_safe_tools)
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            rows = db.execute("""SELECT step_id,tool,requires_confirmation,attempts,max_attempts
                FROM mission_steps WHERE mission_id=? AND status='running'""",
                (mission_id,)
            ).fetchall()
            for step_id, tool_name, requires_confirmation, attempts, max_attempts in rows:
                if attempts >= max_attempts:
                    status = "failed"
                    message = "Interruption pendant l'action ; nombre maximal de tentatives atteint"
                elif tool_name in safe_tools and not requires_confirmation:
                    status = "pending"
                    message = "Interruption détectée ; reprise automatique sûre"
                else:
                    status = "waiting_approval"
                    message = "Résultat incertain après interruption ; vérification humaine requise avant reprise"
                db.execute("""UPDATE mission_steps SET status=?,approved=0,last_result=?
                    WHERE mission_id=? AND step_id=? AND status='running'""",
                    (status, message, mission_id, step_id))
                self._event(db, mission_id, "Reprise : " + step_id + " — " + message)

    def claim_step(self, mission_id: str, step_id: str):
        """Atomically move one pending step to running and consume one attempt."""
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            cur = db.execute("""UPDATE mission_steps SET status='running',
                    attempts=attempts+1,last_result=''
                WHERE mission_id=? AND step_id=? AND status='pending'
                    AND attempts < max_attempts""",
                (mission_id, step_id)
            )
            if cur.rowcount != 1:
                return None
            row = db.execute(
                "SELECT attempts FROM mission_steps WHERE mission_id=? AND step_id=?",
                (mission_id, step_id)
            ).fetchone()
            return row[0]

    def snapshot(self, mission_id: str):
        with self._connect() as db:
            mission = db.execute(
                "SELECT objective,status,cancel_requested FROM missions WHERE mission_id=?",
                (mission_id,)
            ).fetchone()
            if not mission:
                raise KeyError(mission_id)
            steps = db.execute("""SELECT step_id,title,tool,arguments,depends_on,
                requires_confirmation,approved,max_attempts,attempts,status,last_result
                FROM mission_steps WHERE mission_id=? ORDER BY rowid""",
                (mission_id,)
            ).fetchall()
            events = db.execute(
                "SELECT message FROM mission_events WHERE mission_id=? ORDER BY event_id",
                (mission_id,)
            ).fetchall()
        return mission, steps, tuple(event[0] for event in events)


class MissionRunner:
    """Run bounded steps under a renewable lease; only verified results complete."""

    def __init__(self, store: MissionStore, tools: Dict[str, MissionTool],
                 granted_permissions=frozenset(), *, lease_seconds: float = 60.0,
                 heartbeat_interval: float = 10.0):
        if not isinstance(lease_seconds, (int, float)) or not math.isfinite(lease_seconds) or lease_seconds <= 0:
            raise ValueError("lease_seconds must be a positive finite number")
        if not isinstance(heartbeat_interval, (int, float)) or not math.isfinite(heartbeat_interval):
            raise ValueError("heartbeat_interval must be a positive finite number")
        if heartbeat_interval <= 0 or heartbeat_interval >= lease_seconds:
            raise ValueError("heartbeat_interval must be smaller than lease_seconds")
        self.store = store
        self.tools = dict(tools)
        self.granted_permissions = frozenset(granted_permissions)
        self.lease_seconds = float(lease_seconds)
        self.heartbeat_interval = float(heartbeat_interval)

    def run(self, mission_id: str, max_steps: int = 20, max_seconds: float = 300.0) -> MissionReport:
        if not isinstance(max_steps, int) or isinstance(max_steps, bool) or max_steps < 1:
            raise ValueError("max_steps must be a positive integer")
        if not isinstance(max_seconds, (int, float)) or not math.isfinite(max_seconds) or max_seconds <= 0:
            raise ValueError("max_seconds must be a positive finite number")

        owner_token = str(uuid.uuid4())
        if not self.store.acquire_runner_lease(mission_id, owner_token, self.lease_seconds):
            raise MissionAlreadyRunningError("this mission is already being executed")

        stop_heartbeat = threading.Event()
        lease_lost = threading.Event()

        def keep_lease_alive():
            while not stop_heartbeat.wait(self.heartbeat_interval):
                try:
                    if not self.store.renew_runner_lease(
                        mission_id, owner_token, self.lease_seconds
                    ):
                        lease_lost.set()
                        return
                except Exception:
                    lease_lost.set()
                    return

        heartbeat = threading.Thread(
            target=keep_lease_alive, name="bozer-mission-lease", daemon=True
        )
        heartbeat.start()
        try:
            self.store.recover_interrupted_steps(
                mission_id,
                {name for name, tool in self.tools.items() if tool.retry_safe}
            )
            return self._run_locked(
                mission_id, max_steps, float(max_seconds), lease_lost
            )
        finally:
            stop_heartbeat.set()
            heartbeat.join(timeout=max(1.0, self.heartbeat_interval + 1.0))
            self.store.release_runner_lease(mission_id, owner_token)

    def _run_locked(self, mission_id, max_steps, max_seconds, lease_lost):
        started, executed = time.monotonic(), 0
        while executed < max_steps and time.monotonic() - started < max_seconds:
            if lease_lost.is_set():
                self._set_mission_status(
                    mission_id, "paused",
                    "Verrou d'exécution perdu ; reprise prudente nécessaire"
                )
                break
            mission, rows, _ = self.store.snapshot(mission_id)
            objective, status, cancel = mission
            if status in TERMINAL_MISSION_STATES:
                break
            if cancel:
                self._set_mission_status(mission_id, "cancelled", "Mission arrêtée")
                break

            states = {row[0]: row[9] for row in rows}
            if all(step_state == "succeeded" for step_state in states.values()):
                self._set_mission_status(mission_id, "completed", "Objectif terminé")
                break

            progressed = False
            for row in rows:
                (step_id, title, tool_name, args_json, deps_json, needs_confirmation,
                 approved, max_attempts, attempts, step_status, last_result) = row
                if step_status != "pending":
                    continue
                dependencies = json.loads(deps_json)
                if any(states.get(dep) != "succeeded" for dep in dependencies):
                    continue
                tool = self.tools.get(tool_name)
                if tool is None:
                    self._set_step(
                        mission_id, step_id, "blocked", "Outil non disponible : " + tool_name
                    )
                    self._event(mission_id, "Étape bloquée : outil absent " + tool_name)
                    progressed = True
                    break
                if tool.required_permission and tool.required_permission not in self.granted_permissions:
                    self._set_step(
                        mission_id, step_id, "blocked",
                        "Permission absente : " + tool.required_permission
                    )
                    self._event(mission_id, "Étape bloquée : permission absente")
                    progressed = True
                    break

                confirmation_required = bool(needs_confirmation or tool.requires_confirmation)
                if confirmation_required and not approved:
                    self._set_step(
                        mission_id, step_id, "waiting_approval",
                        "Autorisation humaine requise"
                    )
                    self._event(mission_id, "En attente d'autorisation : " + title)
                    progressed = True
                    break
                if attempts >= max_attempts:
                    self._set_step(
                        mission_id, step_id, "failed",
                        last_result or "Nombre maximal d'essais atteint"
                    )
                    self._event(mission_id, "Échec définitif : " + title)
                    progressed = True
                    break

                claimed_attempt = self.store.claim_step(mission_id, step_id)
                if claimed_attempt is None:
                    progressed = True
                    break
                self._set_mission_status(mission_id, "running")
                try:
                    result = tool.handler(json.loads(args_json))
                    if not isinstance(result, ToolResult):
                        raise TypeError("tool handler must return ToolResult")
                except Exception as exc:
                    result = ToolResult(
                        False, "Erreur outil : " + type(exc).__name__, retryable=True
                    )
                executed += 1

                if lease_lost.is_set():
                    self._set_mission_status(
                        mission_id, "paused",
                        "Verrou perdu pendant l'action ; résultat conservé pour vérification"
                    )
                    progressed = True
                    break

                if result.success and result.verified:
                    self._set_step(
                        mission_id, step_id, "succeeded", result.summary, claimed_attempt
                    )
                    self._event(
                        mission_id, "Vérifié : " + title + " — " + result.summary
                    )
                elif result.retryable and claimed_attempt < max_attempts:
                    if tool.retry_safe and not confirmation_required:
                        self._set_step(
                            mission_id, step_id, "pending", result.summary, claimed_attempt
                        )
                        self._event(mission_id, "Nouvel essai sûr prévu : " + title)
                    else:
                        self._set_step(
                            mission_id, step_id, "waiting_approval",
                            result.summary or "Nouvel essai à confirmer",
                            claimed_attempt, approved=0
                        )
                        self._event(
                            mission_id,
                            "Nouvel essai suspendu jusqu'à confirmation humaine : " + title
                        )
                else:
                    self._set_step(
                        mission_id, step_id, "failed",
                        result.summary or "Résultat non vérifié", claimed_attempt
                    )
                    self._event(mission_id, "Échec/non vérifié : " + title)
                progressed = True
                break

            if not progressed:
                mission, rows, _ = self.store.snapshot(mission_id)
                current = [row[9] for row in rows]
                if any(step_state == "waiting_approval" for step_state in current):
                    self._set_mission_status(
                        mission_id, "paused", "Mission en attente d'une autorisation"
                    )
                elif any(step_state in {"failed", "blocked"} for step_state in current):
                    self._set_mission_status(
                        mission_id, "failed", "Mission bloquée par une étape"
                    )
                elif all(step_state == "succeeded" for step_state in current):
                    self._set_mission_status(mission_id, "completed", "Objectif terminé")
                elif any(step_state == "running" for step_state in current):
                    self._set_mission_status(
                        mission_id, "paused", "Une étape nécessite une reprise prudente"
                    )
                else:
                    self._set_mission_status(
                        mission_id, "failed", "Aucune étape exécutable"
                    )
                break

        mission, rows, events = self.store.snapshot(mission_id)
        objective, status, cancel = mission
        if status not in TERMINAL_MISSION_STATES and status != "paused":
            self._set_mission_status(
                mission_id, "limit_reached",
                "Limite d'exécution atteinte ; reprise possible"
            )
            mission, rows, events = self.store.snapshot(mission_id)
            objective, status, cancel = mission
        statuses = [row[9] for row in rows]
        return MissionReport(
            mission_id, objective, status,
            statuses.count("succeeded"), len(statuses),
            statuses.count("failed") + statuses.count("blocked"),
            statuses.count("waiting_approval"), events
        )

    def _set_step(self, mission_id, step_id, status, result, attempts=None, approved=None):
        if status not in VALID_STEP_STATES:
            raise ValueError("invalid step status: " + str(status))
        with self.store._connect() as db:
            if attempts is None and approved is None:
                cur = db.execute(
                    "UPDATE mission_steps SET status=?,last_result=? WHERE mission_id=? AND step_id=?",
                    (status, str(result)[:1000], mission_id, step_id)
                )
            elif attempts is None:
                cur = db.execute(
                    "UPDATE mission_steps SET status=?,last_result=?,approved=? WHERE mission_id=? AND step_id=?",
                    (status, str(result)[:1000], int(approved), mission_id, step_id)
                )
            elif approved is None:
                cur = db.execute(
                    "UPDATE mission_steps SET status=?,last_result=?,attempts=? WHERE mission_id=? AND step_id=?",
                    (status, str(result)[:1000], attempts, mission_id, step_id)
                )
            else:
                cur = db.execute(
                    """UPDATE mission_steps SET status=?,last_result=?,attempts=?,approved=?
                    WHERE mission_id=? AND step_id=?""",
                    (status, str(result)[:1000], attempts, int(approved), mission_id, step_id)
                )
            if cur.rowcount != 1:
                raise KeyError((mission_id, step_id))

    def _set_mission_status(self, mission_id, status, event=None):
        with self.store._connect() as db:
            cur = db.execute(
                "UPDATE missions SET status=?,updated_at=? WHERE mission_id=?",
                (status, time.time(), mission_id)
            )
            if cur.rowcount != 1:
                raise KeyError(mission_id)
            if event:
                self.store._event(db, mission_id, event)

    def _event(self, mission_id, message):
        with self.store._connect() as db:
            self.store._event(db, mission_id, message)
