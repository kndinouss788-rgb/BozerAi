"""Provider-neutral language-model boundary for BOZER.

No network client, API key, or paid provider is bundled here. A provider is
injected by the host application and is called only when explicitly enabled.
"""
from dataclasses import dataclass
from typing import Optional, Protocol


@dataclass(frozen=True)
class ModelRequest:
    prompt: str
    max_output_chars: int = 6000


@dataclass(frozen=True)
class ModelResponse:
    text: str
    provider_name: str
    model_name: Optional[str] = None


class LanguageModelProvider(Protocol):
    """Minimal contract implemented by a local or remote model adapter."""

    @property
    def provider_name(self) -> str:
        ...

    def generate(self, request: ModelRequest) -> ModelResponse:
        ...


@dataclass(frozen=True)
class ModelResult:
    ok: bool
    text: str
    provider_name: Optional[str]
    model_name: Optional[str]
    error: Optional[str]


class ModelGateway:
    """Validates model calls and makes provider use an explicit opt-in."""

    def __init__(self, provider: Optional[LanguageModelProvider] = None):
        self.provider = provider

    def generate(
        self,
        prompt: str,
        *,
        enabled: bool = False,
        max_output_chars: int = 6000,
    ) -> ModelResult:
        if not isinstance(prompt, str) or not prompt.strip():
            return ModelResult(False, "", None, None, "empty_prompt")
        if max_output_chars < 1:
            return ModelResult(False, "", None, None, "invalid_output_limit")
        if not enabled:
            return ModelResult(False, "", None, None, "provider_use_not_enabled")
        if self.provider is None:
            return ModelResult(False, "", None, None, "provider_not_configured")

        try:
            response = self.provider.generate(
                ModelRequest(prompt=prompt, max_output_chars=max_output_chars)
            )
        except Exception:
            # Do not leak credentials, provider payloads, or internal exception text.
            return ModelResult(False, "", self.provider.provider_name, None, "provider_error")

        if not isinstance(response, ModelResponse):
            return ModelResult(False, "", self.provider.provider_name, None, "invalid_provider_response")
        if not isinstance(response.text, str) or not response.text.strip():
            return ModelResult(False, "", response.provider_name, response.model_name, "empty_provider_response")

        return ModelResult(
            True,
            response.text.strip()[:max_output_chars],
            response.provider_name,
            response.model_name,
            None,
        )
