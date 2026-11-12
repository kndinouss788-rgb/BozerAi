"""Explicit-cost provider routing for BOZER's model gateway.

The router is a provider adapter, not a model gateway. It preserves the
ModelGateway's per-call opt-in and adds an additional explicit switch before
any metered fallback can be attempted.
"""
from dataclasses import dataclass
from enum import Enum
from typing import Tuple

from .model_gateway import ModelRequest, ModelResponse


class ProviderCostClass(str, Enum):
    LOCAL = "local"
    METERED = "metered"


class ModelProvider:
    """Structural contract for a provider accepted by the router."""

    @property
    def provider_name(self) -> str:
        raise NotImplementedError

    def generate(self, request: ModelRequest) -> ModelResponse:
        raise NotImplementedError


@dataclass(frozen=True)
class ProviderCandidate:
    provider: ModelProvider
    cost_class: ProviderCostClass

    def __post_init__(self):
        if not isinstance(self.cost_class, ProviderCostClass):
            raise ValueError("provider cost class must be LOCAL or METERED")
        if not callable(getattr(self.provider, "generate", None)):
            raise ValueError("provider must expose generate(request)")


class ProviderRouter:
    """Try configured providers in order with an explicit metered-fallback gate."""

    provider_name = "bozer-provider-router"

    def __init__(
        self,
        candidates: Tuple[ProviderCandidate, ...],
        *,
        allow_metered_fallback: bool = False,
        max_metered_attempts: int = 1,
    ):
        if not candidates:
            raise ValueError("at least one provider candidate is required")
        if any(not isinstance(candidate, ProviderCandidate) for candidate in candidates):
            raise ValueError("all candidates must be ProviderCandidate instances")
        if (
            isinstance(max_metered_attempts, bool)
            or not isinstance(max_metered_attempts, int)
            or not 0 <= max_metered_attempts <= 2
        ):
            raise ValueError("max_metered_attempts must be between 0 and 2")
        self.candidates = tuple(candidates)
        self.allow_metered_fallback = bool(allow_metered_fallback)
        self.max_metered_attempts = max_metered_attempts

    def generate(self, request: ModelRequest) -> ModelResponse:
        if not isinstance(request, ModelRequest) or not request.prompt.strip():
            raise ValueError("valid model request required")

        metered_attempts = 0
        for candidate in self.candidates:
            if candidate.cost_class == ProviderCostClass.METERED:
                if not self.allow_metered_fallback:
                    continue
                if metered_attempts >= self.max_metered_attempts:
                    continue
                metered_attempts += 1

            try:
                response = candidate.provider.generate(request)
            except Exception:
                continue

            if (
                isinstance(response, ModelResponse)
                and isinstance(response.text, str)
                and response.text.strip()
            ):
                return response

        # Do not expose provider internals, API responses or secrets.
        raise RuntimeError("no_eligible_provider_succeeded")
