"""BOZER NEURAL memory primitives: typed, provenance-aware, user-controlled.

This is a storage-independent reference core. It does not persist data or infer
consent; Android must provide encrypted storage and enforce retention/deletion.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any
import uuid


def _parse_time(value: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError) as exc:
        raise ValueError("timestamps must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise ValueError("timestamps must include a timezone")
    return parsed.astimezone(timezone.utc)


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
        if not isinstance(content, str) or not content.strip() or not kind.strip() or not source.strip():
            raise ValueError("content, kind and source are required")
        if not 0.0 <= confidence <= 1.0:
            raise ValueError("confidence must be between 0 and 1")
        if sensitivity not in {"ordinary", "private", "sensitive"}:
            raise ValueError("unsupported sensitivity")
        if expires_at is not None:
            _parse_time(expires_at)
        return cls(uuid.uuid4().hex, kind.strip(), content.strip(),
                   datetime.now(timezone.utc).isoformat(), source.strip(),
                   confidence, sensitivity, expires_at, dict(metadata or {}))


class MemoryStore:
    """In-memory reference implementation; replace with encrypted user storage."""
    def __init__(self):
        self._records: dict[str, MemoryRecord] = {}

    def add(self, record: MemoryRecord) -> None:
        self._records[record.id] = record

    def recall(self, *, kind: str | None = None, include_private: bool = False,
               now: datetime | None = None):
        moment = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
        expired = [
            key for key, record in self._records.items()
            if record.expires_at is not None and _parse_time(record.expires_at) <= moment
        ]
        for key in expired:
            self._records.pop(key, None)
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
