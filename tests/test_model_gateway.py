import json
import unittest
from unittest.mock import patch

from bozer_brain.model_gateway import (
    ModelGateway, ModelRequest, ModelResponse, OpenAICompatibleProvider,
    openai_provider_from_env,
)


class FakeProvider:
    provider_name = "fake-local"

    def __init__(self, response=None, error=False):
        self.response = response or ModelResponse(" Réponse test ", "fake-local", "unit")
        self.error = error
        self.requests = []

    def generate(self, request: ModelRequest):
        self.requests.append(request)
        if self.error:
            raise RuntimeError("sensitive provider detail")
        return self.response


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self, size=-1): return self.payload[:size]


class ModelGatewayTests(unittest.TestCase):
    def test_provider_is_not_called_without_explicit_opt_in(self):
        provider = FakeProvider()
        result = ModelGateway(provider).generate("Bonjour")
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "provider_use_not_enabled")
        self.assertEqual(provider.requests, [])

    def test_missing_provider_is_reported(self):
        self.assertEqual(ModelGateway().generate("Bonjour", enabled=True).error,
                         "provider_not_configured")

    def test_successful_response_is_trimmed_and_bounded(self):
        provider = FakeProvider(ModelResponse("  réponse longue  ", "local", "small-model"))
        result = ModelGateway(provider).generate("Bonjour", enabled=True, max_output_chars=8)
        self.assertTrue(result.ok)
        self.assertEqual(result.text, "réponse")
        self.assertEqual(provider.requests[0].prompt, "Bonjour")

    def test_provider_error_does_not_leak_exception_details(self):
        result = ModelGateway(FakeProvider(error=True)).generate("Bonjour", enabled=True)
        self.assertEqual(result.error, "provider_error")
        self.assertNotIn("sensitive", result.error)

    def test_empty_and_invalid_requests_are_rejected(self):
        gateway = ModelGateway(FakeProvider())
        self.assertEqual(gateway.generate(" ").error, "empty_prompt")
        self.assertEqual(gateway.generate("test", enabled=True, max_output_chars=0).error,
                         "invalid_output_limit")

    def test_invalid_provider_response_is_rejected(self):
        result = ModelGateway(FakeProvider(response="bad")).generate("Bonjour", enabled=True)
        self.assertEqual(result.error, "invalid_provider_response")

    def test_empty_provider_text_is_rejected(self):
        result = ModelGateway(FakeProvider(ModelResponse(" ", "fake"))).generate(
            "Bonjour", enabled=True)
        self.assertEqual(result.error, "empty_provider_response")

    @patch("bozer_brain.model_gateway.urlopen")
    def test_openai_compatible_provider_sends_bearer_request_and_parses_reply(self, mocked):
        mocked.return_value = FakeResponse(json.dumps({
            "model": "demo-model",
            "choices": [{"message": {"content": "Bonjour depuis le modèle"}}]
        }).encode())
        provider = OpenAICompatibleProvider("secret-test", "demo-model")
        response = provider.generate(ModelRequest("Bonjour"))
        request = mocked.call_args.args[0]
        self.assertEqual(request.full_url, "https://api.openai.com/v1/chat/completions")
        self.assertEqual(request.get_header("Authorization"), "Bearer secret-test")
        self.assertEqual(response.text, "Bonjour depuis le modèle")
        self.assertEqual(response.model_name, "demo-model")

    def test_provider_rejects_non_https_endpoint(self):
        with self.assertRaises(ValueError):
            OpenAICompatibleProvider("key", "model", "http://example.com/v1")

    def test_environment_factory_needs_both_key_and_model(self):
        self.assertIsNone(openai_provider_from_env({}))
        provider = openai_provider_from_env({
            "BOZER_MODEL_API_KEY": "secret", "BOZER_MODEL_NAME": "demo",
        })
        self.assertIsInstance(provider, OpenAICompatibleProvider)


if __name__ == "__main__":
    unittest.main()
