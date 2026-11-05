"""Small local SQLite memory for conversation history and explicit user facts.

The caller chooses the database path. No network access or device actions happen here.
Sensitive or personal facts should only be stored after an explicit user request.
"""
import sqlite3
from pathlib import Path
from typing import List, Optional, Tuple


class ConversationMemory:
    def __init__(self, database_path: str):
        self.database_path = str(database_path)
        Path(self.database_path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as db:
            db.execute("""
                CREATE TABLE IF NOT EXISTS turns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """)
            db.execute("""
                CREATE TABLE IF NOT EXISTS facts (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """)

    def _connect(self):
        return sqlite3.connect(self.database_path)

    def add_turn(self, role: str, content: str) -> None:
        if role not in {"user", "assistant"}:
            raise ValueError("role must be 'user' or 'assistant'")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("content must not be empty")
        with self._connect() as db:
            db.execute("INSERT INTO turns(role, content) VALUES (?, ?)", (role, content.strip()))

    def recent_turns(self, limit: int = 12) -> List[Tuple[str, str]]:
        if limit < 1:
            raise ValueError("limit must be positive")
        with self._connect() as db:
            rows = db.execute(
                "SELECT role, content FROM turns ORDER BY id DESC LIMIT ?", (limit,)
            ).fetchall()
        return list(reversed(rows))

    def remember_fact(self, key: str, value: str) -> None:
        clean_key, clean_value = key.strip(), value.strip()
        if not clean_key or not clean_value:
            raise ValueError("fact key and value must not be empty")
        with self._connect() as db:
            db.execute(
                """INSERT INTO facts(key, value) VALUES (?, ?)
                   ON CONFLICT(key) DO UPDATE SET
                   value=excluded.value, updated_at=CURRENT_TIMESTAMP""",
                (clean_key, clean_value),
            )

    def get_fact(self, key: str) -> Optional[str]:
        with self._connect() as db:
            row = db.execute("SELECT value FROM facts WHERE key = ?", (key.strip(),)).fetchone()
        return row[0] if row else None

    def forget_fact(self, key: str) -> bool:
        with self._connect() as db:
            cursor = db.execute("DELETE FROM facts WHERE key = ?", (key.strip(),))
            return cursor.rowcount > 0

    def clear_history(self) -> None:
        with self._connect() as db:
            db.execute("DELETE FROM turns")
