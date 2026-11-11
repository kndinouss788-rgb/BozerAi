"""Persistent, bounded mission runner for BOZER ACTION.

This is an execution engine, not an AI planner or an Android/cloud runtime.
A trusted host supplies a planner and registered tool handlers. Every run is
bounded, checkpointed in SQLite, cancellable, and stops at approval gates.
"""
import json
import sqlite3
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, Iterable, Optional, Tuple


TERMINAL_MISSION_STATES = {"completed", "failed", "cancelled", "limit_reached"}
VALID_STEP_STATES = {"pending", "running", "succeeded", "failed", "waiting_approval", "blocked"}


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


class MissionStore:
    """SQLite-backed mission state; safe to reopen after process restart."""

    def __init__(self, database_path: str):
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
                created_at REAL NOT NULL, message TEXT NOT NULL
            )""")

    def _connect(self):
        db = sqlite3.connect(self.database_path, timeout=15)
        db.execute("PRAGMA foreign_keys=ON")
        return db

    def create(self, objective: str, steps: Iterable[MissionStep]) -> str:
        objective = objective.strip()
        items = tuple(steps)
        if not objective or not items:
            raise ValueError("objective and at least one step are required")
        ids = [s.step_id for s in items]
        if any(not x.strip() for x in ids) or len(ids) != len(set(ids)):
            raise ValueError("step IDs must be non-empty and unique")
        known = set(ids)
        for step in items:
            if not step.title.strip() or not step.tool.strip() or step.max_attempts < 1:
                raise ValueError("each step needs title, tool, and max_attempts >= 1")
            if any(dep not in known or dep == step.step_id for dep in step.depends_on):
                raise ValueError("step dependency is missing or self-referential")
            json.dumps(step.arguments)
        self._assert_acyclic(items)
        mission_id = str(uuid.uuid4())
        now = time.time()
        with self._connect() as db:
            db.execute(
                "INSERT INTO missions(mission_id,objective,status,created_at,updated_at) VALUES(?,?,?,?,?)",
                (mission_id, objective, "queued", now, now),
            )
            for s in items:
                db.execute("""INSERT INTO mission_steps(
                    mission_id,step_id,title,tool,arguments,depends_on,
                    requires_confirmation,max_attempts,status
                ) VALUES(?,?,?,?,?,?,?,?,?)""",
                    (mission_id, s.step_id, s.title, s.tool, json.dumps(s.arguments),
                     json.dumps(list(s.depends_on)), int(s.requires_confirmation),
                     s.max_attempts, "pending"))
            self._event(db, mission_id, "Mission créée")
        return mission_id

    @staticmethod
    def _assert_acyclic(steps):
        done, pending = set(), list(steps)
        while pending:
            ready = next((s for s in pending if set(s.depends_on).issubset(done)), None)
            if ready is None:
                raise ValueError("mission dependency graph contains a cycle")
            done.add(ready.step_id)
            pending.remove(ready)

    @staticmethod
    def _event(db, mission_id, message):
        db.execute("INSERT INTO mission_events(mission_id,created_at,message) VALUES(?,?,?)",
                   (mission_id, time.time(), message))

    def request_cancel(self, mission_id: str):
        with self._connect() as db:
            cur = db.execute("UPDATE missions SET cancel_requested=1,updated_at=? WHERE mission_id=?",
                             (time.time(), mission_id))
            if not cur.rowcount:
                raise KeyError(mission_id)
            self._event(db, mission_id, "Annulation demandée")

    def approve_step(self, mission_id: str, step_id: str):
        with self._connect() as db:
            cur = db.execute("""UPDATE mission_steps SET approved=1
                WHERE mission_id=? AND step_id=? AND status='waiting_approval'""",
                (mission_id, step_id))
            if not cur.rowcount:
                raise ValueError("step is not waiting for approval")
            db.execute("UPDATE mission_steps SET status='pending' WHERE mission_id=? AND step_id=?",
                       (mission_id, step_id))
            db.execute("UPDATE missions SET status='queued',updated_at=? WHERE mission_id=?",
                       (time.time(), mission_id))
            self._event(db, mission_id, "Autorisation accordée pour " + step_id)

    def snapshot(self, mission_id: str):
        with self._connect() as db:
            mission = db.execute("SELECT objective,status,cancel_requested FROM missions WHERE mission_id=?",
                                 (mission_id,)).fetchone()
            if not mission:
                raise KeyError(mission_id)
            steps = db.execute("""SELECT step_id,title,tool,arguments,depends_on,
                requires_confirmation,approved,max_attempts,attempts,status,last_result
                FROM mission_steps WHERE mission_id=? ORDER BY rowid""", (mission_id,)).fetchall()
            events = db.execute("SELECT message FROM mission_events WHERE mission_id=? ORDER BY event_id",
                                (mission_id,)).fetchall()
        return mission, steps, tuple(e[0] for e in events)


class MissionRunner:
    """Run ready steps until done, blocked, cancelled, or a resource limit is reached."""

    def __init__(self, store: MissionStore, tools: Dict[str, MissionTool],
                 granted_permissions=frozenset()):
        self.store = store
        self.tools = dict(tools)
        self.granted_permissions = frozenset(granted_permissions)

    def run(self, mission_id: str, max_steps: int = 20, max_seconds: float = 300.0) -> MissionReport:
        if max_steps < 1 or max_seconds <= 0:
            raise ValueError("execution limits must be positive")
        started, executed = time.monotonic(), 0
        while executed < max_steps and time.monotonic() - started < max_seconds:
            mission, rows, _ = self.store.snapshot(mission_id)
            objective, status, cancel = mission
            if status in TERMINAL_MISSION_STATES:
                break
            if cancel:
                self._set_mission_status(mission_id, "cancelled", "Mission arrêtée")
                break

            states = {r[0]: r[9] for r in rows}
            if all(s == "succeeded" for s in states.values()):
                self._set_mission_status(mission_id, "completed", "Objectif terminé")
                break

            progressed = False
            for row in rows:
                (step_id,title,tool_name,args_json,deps_json,needs_confirmation,approved,
                 max_attempts,attempts,step_status,last_result) = row
                if step_status not in {"pending", "failed"}:
                    continue
                deps = json.loads(deps_json)
                if any(states.get(dep) != "succeeded" for dep in deps):
                    continue
                if needs_confirmation and not approved:
                    self._set_step(mission_id, step_id, "waiting_approval",
                                   "Autorisation humaine requise")
                    self._event(mission_id, "En attente d'autorisation : " + title)
                    progressed = True
                    break
                tool = self.tools.get(tool_name)
                if tool is None:
                    self._set_step(mission_id, step_id, "blocked", "Outil non disponible : " + tool_name)
                    self._event(mission_id, "Étape bloquée : outil absent " + tool_name)
                    progressed = True
                    break
                if tool.required_permission and tool.required_permission not in self.granted_permissions:
                    self._set_step(mission_id, step_id, "blocked",
                                   "Permission absente : " + tool.required_permission)
                    self._event(mission_id, "Étape bloquée : permission absente")
                    progressed = True
                    break
                if tool.requires_confirmation and not approved:
                    self._set_step(mission_id, step_id, "waiting_approval", "Confirmation requise par l'outil")
                    self._event(mission_id, "En attente d'autorisation : " + title)
                    progressed = True
                    break
                if attempts >= max_attempts:
                    self._set_step(mission_id, step_id, "failed", last_result or "Nombre maximal d'essais atteint")
                    self._event(mission_id, "Échec définitif : " + title)
                    progressed = True
                    break

                self._set_step(mission_id, step_id, "running", "")
                self._set_mission_status(mission_id, "running")
                try:
                    result = tool.handler(json.loads(args_json))
                    if not isinstance(result, ToolResult):
                        raise TypeError("tool handler must return ToolResult")
                except Exception as exc:
                    result = ToolResult(False, "Erreur outil : " + type(exc).__name__, retryable=True)
                executed += 1
                attempts += 1
                if result.success and result.verified:
                    self._set_step(mission_id, step_id, "succeeded", result.summary, attempts)
                    self._event(mission_id, "Vérifié : " + title + " — " + result.summary)
                elif result.retryable and attempts < max_attempts:
                    self._set_step(mission_id, step_id, "pending", result.summary, attempts)
                    self._event(mission_id, "Nouvel essai prévu : " + title)
                else:
                    self._set_step(mission_id, step_id, "failed", result.summary or "Résultat non vérifié", attempts)
                    self._event(mission_id, "Étape échouée/non vérifiée : " + title)
                progressed = True
                break

            if not progressed:
                mission, rows, _ = self.store.snapshot(mission_id)
                current = [r[9] for r in rows]
                if any(s == "waiting_approval" for s in current):
                    self._set_mission_status(mission_id, "paused", "Mission en attente d'une autorisation")
                elif any(s in {"failed", "blocked"} for s in current):
                    self._set_mission_status(mission_id, "failed", "Mission bloquée par une étape")
                elif all(s == "succeeded" for s in current):
                    self._set_mission_status(mission_id, "completed", "Objectif terminé")
                else:
                    self._set_mission_status(mission_id, "failed", "Aucune étape exécutable")
                break

        mission, rows, events = self.store.snapshot(mission_id)
        objective, status, cancel = mission
        if status not in TERMINAL_MISSION_STATES and status != "paused":
            self._set_mission_status(mission_id, "limit_reached", "Limite d'exécution atteinte ; reprise possible")
            mission, rows, events = self.store.snapshot(mission_id)
            objective, status, cancel = mission
        statuses = [r[9] for r in rows]
        return MissionReport(mission_id, objective, status,
            statuses.count("succeeded"), len(statuses), statuses.count("failed") + statuses.count("blocked"),
            statuses.count("waiting_approval"), events)

    def _set_step(self, mission_id, step_id, status, result, attempts=None):
        with self.store._connect() as db:
            if attempts is None:
                db.execute("UPDATE mission_steps SET status=?,last_result=? WHERE mission_id=? AND step_id=?",
                           (status,result,mission_id,step_id))
            else:
                db.execute("UPDATE mission_steps SET status=?,last_result=?,attempts=? WHERE mission_id=? AND step_id=?",
                           (status,result,attempts,mission_id,step_id))
            db.execute("UPDATE missions SET updated_at=? WHERE mission_id=?", (time.time(),mission_id))

    def _set_mission_status(self, mission_id, status, event=None):
        with self.store._connect() as db:
            db.execute("UPDATE missions SET status=?,updated_at=? WHERE mission_id=?",
                       (status,time.time(),mission_id))
            if event:
                self.store._event(db, mission_id, event)

    def _event(self, mission_id, message):
        with self.store._connect() as db:
            self.store._event(db, mission_id, message)
