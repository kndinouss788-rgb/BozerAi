"""Outcome verification contracts.

The verifier does not execute actions and does not assume that an adapter's
success flag proves the intended state changed. It compares an expected state
with independently observed state supplied by a trusted platform adapter.
"""
from dataclasses import dataclass
from enum import Enum
from typing import Optional


class VerificationStatus(str, Enum):
    VERIFIED = "verified"
    FAILED = "failed"
    UNVERIFIED = "unverified"


@dataclass(frozen=True)
class ActionOutcome:
    status: VerificationStatus
    explanation: str
    expected_state: Optional[str]
    observed_state: Optional[str]


def verify_outcome(
    expected_state: Optional[str],
    observed_state: Optional[str],
    adapter_reported_success: Optional[bool],
) -> ActionOutcome:
    if adapter_reported_success is False:
        return ActionOutcome(
            VerificationStatus.FAILED,
            "L'adaptateur a signalé un échec ; BOZER ne doit pas annoncer une réussite.",
            expected_state, observed_state,
        )
    if not expected_state or not observed_state or adapter_reported_success is not True:
        return ActionOutcome(
            VerificationStatus.UNVERIFIED,
            "Résultat non vérifiable : il manque un état attendu, un état observé ou un rapport positif.",
            expected_state, observed_state,
        )
    if expected_state.strip().casefold() != observed_state.strip().casefold():
        return ActionOutcome(
            VerificationStatus.FAILED,
            "L'état observé ne correspond pas à l'objectif demandé.",
            expected_state, observed_state,
        )
    return ActionOutcome(
        VerificationStatus.VERIFIED,
        "L'état observé correspond à l'objectif demandé.",
        expected_state, observed_state,
    )
