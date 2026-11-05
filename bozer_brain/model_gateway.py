"""Provider-neutral gateway plus a real OpenAI-compatible HTTP provider.

Credentials are read from the host environment, never committed or embedded
in an Android client. Network use remains behind ModelGateway's explicit opt-in.
"""
import json
import os
from dataclasses import dataclass
from typing import Optional, Protocol
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


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


class OpenAICompatibleProvider:
    """HTTP adapter for /chat/completions providers using Bearer API keys."""

    def __init__(self, api_key, model, base_url="https://api.openai.com/v1",
                 timeout=25.0, opener=None):
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValueError("api_key is required")
        if not isinstance(model, str) or not model.strip():
            raise ValueError("model is required")
        parsed = urlsplit(base_url)
        if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password:
            raise ValueError("base_url must be an HTTPS origin or API path")
        if parsed.query or parsed.fragment:
            raise ValueError("base_url must not contain query or fragment")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self._api_key = api_key.strip()
        self.model = model.strip()
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._opener = opener or urlopen

    @property
    def provider_name(self):
        return "openai-compatible"

    def generate(self, request):
        if not isinstance(request, ModelRequest) or not request.prompt.strip():
            raise ValueError("valid model request required")
        endpoint = self.base_url + "/chat/completions"
        body = json.dumps({
            "model": self.model,
            "messages": [{"role": "user", "content": request.prompt}],
            "max_tokens": max(64, min(2048, request.max_output_chars // 4)),
            "stream": False,
        }).encode("utf-8")
        http_request = Request(endpoint, data=body, method="POST", headers={
            "Authorization": "Bearer " + self._api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        })
        with self._opener(http_request, timeout=self.timeout) as response:
            payload = response.read(1_000_001)
        if len(payload) > 1_000_000:
            raise ValueError("model response too large")
        data = json.loads(payload.decode("utf-8"))
        choices = data.get("choices", [])
        if not choices:
            raise ValueError("model response has no choices")
        message = choices[0].get("message", {})
        content = message.get("content")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("model response has no text content")
        return ModelResponse(content, self.provider_name, data.get("model") or self.model)


def openai_provider_from_env(environ=None):
    """Build a provider from server-side BOZER_* variables; return None if unset."""
    values = os.environ if environ is None else environ
    api_key = values.get("BOZER_MODEL_API_KEY", "").strip()
    model = values.get("BOZER_MODEL_NAME", "").strip()
    if not api_key or not model:
        return None
    return OpenAICompatibleProvider(
        api_key=api_key,
        model=model,
        base_url=values.get("BOZER_MODEL_BASE_URL", "https://api.openai.com/v1"),
        timeout=float(values.get("BOZER_MODEL_TIMEOUT", "25")),
    )


class ModelGateway:
    """Validates model calls and makes provider use an explicit opt-in."""

    def __init__(self, provider: Optional[LanguageModelProvider] = None):
        self.provider = provider

    def generate(self, prompt, *, enabled=False, max_output_chars=6000):
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
            return ModelResult(False, "", self.provider.provider_name, None, "provider_error")
        if not isinstance(response, ModelResponse):
            return ModelResult(False, "", self.provider.provider_name, None, "invalid_provider_response")
        if not isinstance(response.text, str) or not response.text.strip():
            return ModelResult(False, "", response.provider_name, response.model_name, "empty_provider_response")
        bounded_text = response.text.strip()[:max_output_chars].rstrip()
        return ModelResult(True, bounded_text, response.provider_name, response.model_name, None)
