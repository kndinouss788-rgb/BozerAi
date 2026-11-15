"""BOZER NEURAL memory primitives: typed, provenance-aware, user-controlled.

This is a storage-independent core. It does not persist data or infer consent;
the Android/database adapter must enforce retention and deletion policies.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
import uuid

@dataclass(frozen=True)
class MemoryRecord:
    id: str
    kind: str
    content: str
    created_at: str
    source: str
    confidence: float
    sensitivity: str = "ordinary"
    expires_at: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def create(cls, content: str, *, kind: str, source: str,
               confidence: float = 1.0, sensitivity: str = "ordinary",
               expires_at: str | None = None, metadata: dict | None = None):
        if not content.strip() or not kind.strip() or not source.strip():
            raise ValueError("content, kind and source are required")
        if not 0.0 <= confidence <= 1.0:
            raise ValueError("confidence must be between 0 and 1")
        if sensitivity not in {"ordinary", "private", "sensitive"}:
            raise ValueError("unsupported sensitivity")
        return cls(uuid.uuid4().hex, kind, content.strip(),
                   datetime.now(timezone.utc).isoformat(), source,
                   confidence, sensitivity, expires_at, metadata or {})

class MemoryStore:
    """In-memory reference implementation; replace with encrypted user storage."""
    def __init__(self):
        self._records: dict[str, MemoryRecord] = {}

    def add(self, record: MemoryRecord) -> None:
        self._records[record.id] = record

    def recall(self, *, kind: str | None = None, include_private: bool = False):
        rows = self._records.values()
        return tuple(r for r in rows
                     if (kind is None or r.kind == kind)
                     and (include_private or r.sensitivity == "ordinary"))

    def forget(self, record_id: str) -> bool:
        return self._records.pop(record_id, None) is not None

    def clear(self) -> int:
        count = len(self._records)
        self._records.clear()
        return count
