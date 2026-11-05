"""Provider-neutral gateway with native OpenAI Responses and compatible HTTP adapters."""
import json, os
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
    def provider_name(self) -> str: ...
    def generate(self, request: ModelRequest) -> ModelResponse: ...

@dataclass(frozen=True)
class ModelResult:
    ok: bool
    text: str
    provider_name: Optional[str]
    model_name: Optional[str]
    error: Optional[str]

class _HttpsJsonProvider:
    def __init__(self, api_key, model, base_url, timeout, opener):
        if not isinstance(api_key, str) or not api_key.strip(): raise ValueError("api_key is required")
        if not isinstance(model, str) or not model.strip(): raise ValueError("model is required")
        parsed = urlsplit(base_url)
        if parsed.scheme != "https" or not parsed.netloc or parsed.username or parsed.password: raise ValueError("base_url must be HTTPS")
        if parsed.query or parsed.fragment: raise ValueError("base_url must not contain query or fragment")
        if timeout <= 0: raise ValueError("timeout must be positive")
        self._api_key, self.model = api_key.strip(), model.strip()
        self.base_url, self.timeout = base_url.rstrip("/"), timeout
        self._opener = opener or urlopen
    def _post(self, path, body):
        req = Request(self.base_url + path, data=json.dumps(body).encode(), method="POST", headers={
            "Authorization": "Bearer " + self._api_key, "Content-Type": "application/json", "Accept": "application/json"})
        with self._opener(req, timeout=self.timeout) as response: payload = response.read(1_000_001)
        if len(payload) > 1_000_000: raise ValueError("model response too large")
        data = json.loads(payload.decode("utf-8"))
        if not isinstance(data, dict): raise ValueError("invalid model response")
        return data

class OpenAIResponsesProvider(_HttpsJsonProvider):
    """Native OpenAI Responses API adapter; response storage is disabled."""
    def __init__(self, api_key, model, base_url="https://api.openai.com/v1", timeout=25.0, opener=None):
        super().__init__(api_key, model, base_url, timeout, opener)

    @property
    def provider_name(self): return "openai-responses"
    def generate(self, request):
        if not isinstance(request, ModelRequest) or not request.prompt.strip(): raise ValueError("valid model request required")
        data = self._post("/responses", {"model": self.model, "input": request.prompt,
                         "max_output_tokens": max(64, min(2048, request.max_output_chars // 4)), "store": False})
        texts = []
        direct = data.get("output_text")
        if isinstance(direct, str) and direct.strip(): texts.append(direct)
        for item in data.get("output", []):
            if isinstance(item, dict) and item.get("type") == "message":
                for part in item.get("content", []):
                    if isinstance(part, dict) and part.get("type") == "output_text":
                        value = part.get("text")
                        if isinstance(value, str) and value.strip(): texts.append(value)
        if not texts: raise ValueError("model response has no text content")
        return ModelResponse("\n".join(texts), self.provider_name, data.get("model") or self.model)

class OpenAICompatibleProvider(_HttpsJsonProvider):
    """Adapter for endpoints exposing the /chat/completions contract."""
    def __init__(self, api_key, model, base_url="https://api.openai.com/v1", timeout=25.0, opener=None):
        super().__init__(api_key, model, base_url, timeout, opener)

    @property
    def provider_name(self): return "openai-compatible"
    def generate(self, request):
        if not isinstance(request, ModelRequest) or not request.prompt.strip(): raise ValueError("valid model request required")
        data = self._post("/chat/completions", {"model": self.model,
                         "messages": [{"role": "user", "content": request.prompt}],
                         "max_tokens": max(64, min(2048, request.max_output_chars // 4)), "stream": False})
        choices = data.get("choices", [])
        message = choices[0].get("message", {}) if choices else {}
        content = message.get("content")
        if not isinstance(content, str) or not content.strip(): raise ValueError("model response has no text content")
        return ModelResponse(content, self.provider_name, data.get("model") or self.model)

def openai_provider_from_env(environ=None):
    """Build a provider from trusted-host BOZER_* variables."""
    values = os.environ if environ is None else environ
    key, model = values.get("BOZER_MODEL_API_KEY", "").strip(), values.get("BOZER_MODEL_NAME", "").strip()
    if not key or not model: return None
    base = values.get("BOZER_MODEL_BASE_URL", "https://api.openai.com/v1")
    timeout = float(values.get("BOZER_MODEL_TIMEOUT", "25"))
    style = values.get("BOZER_MODEL_API_STYLE", "responses").strip().lower()
    if style == "responses": return OpenAIResponsesProvider(key, model, base, timeout)
    if style == "chat_completions": return OpenAICompatibleProvider(key, model, base, timeout)
    raise ValueError("BOZER_MODEL_API_STYLE must be responses or chat_completions")

class ModelGateway:
    """Validates model calls and makes provider use an explicit opt-in."""
    def __init__(self, provider: Optional[LanguageModelProvider] = None): self.provider = provider
    def generate(self, prompt, *, enabled=False, max_output_chars=6000):
        if not isinstance(prompt, str) or not prompt.strip(): return ModelResult(False, "", None, None, "empty_prompt")
        if max_output_chars < 1: return ModelResult(False, "", None, None, "invalid_output_limit")
        if not enabled: return ModelResult(False, "", None, None, "provider_use_not_enabled")
        if self.provider is None: return ModelResult(False, "", None, None, "provider_not_configured")
        try: response = self.provider.generate(ModelRequest(prompt, max_output_chars))
        except Exception: return ModelResult(False, "", self.provider.provider_name, None, "provider_error")
        if not isinstance(response, ModelResponse): return ModelResult(False, "", self.provider.provider_name, None, "invalid_provider_response")
        if not isinstance(response.text, str) or not response.text.strip(): return ModelResult(False, "", response.provider_name, response.model_name, "empty_provider_response")
        return ModelResult(True, response.text.strip()[:max_output_chars].rstrip(), response.provider_name, response.model_name, None)
