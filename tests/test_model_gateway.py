import unittest

from bozer_brain.model_gateway import (
    ModelGateway,
    ModelRequest,
    ModelResponse,
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


class ModelGatewayTests(unittest.TestCase):
    def test_provider_is_not_called_without_explicit_opt_in(self):
        provider = FakeProvider()
        result = ModelGateway(provider).generate("Bonjour")
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "provider_use_not_enabled")
        self.assertEqual(provider.requests, [])

    def test_missing_provider_is_reported(self):
        result = ModelGateway().generate("Bonjour", enabled=True)
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "provider_not_configured")

    def test_successful_response_is_trimmed_and_bounded(self):
        provider = FakeProvider(ModelResponse("  réponse longue  ", "local", "small-model"))
        result = ModelGateway(provider).generate(
            "Bonjour", enabled=True, max_output_chars=8
        )
        self.assertTrue(result.ok)
        self.assertEqual(result.text, "réponse")
        self.assertEqual(provider.requests[0].prompt, "Bonjour")

    def test_provider_error_does_not_leak_exception_details(self):
        provider = FakeProvider(error=True)
        result = ModelGateway(provider).generate("Bonjour", enabled=True)
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "provider_error")
        self.assertNotIn("sensitive", result.error)

    def test_empty_and_invalid_requests_are_rejected(self):
        gateway = ModelGateway(FakeProvider())
        self.assertEqual(gateway.generate(" ").error, "empty_prompt")
        self.assertEqual(
            gateway.generate("test", enabled=True, max_output_chars=0).error,
            "invalid_output_limit",
        )

    def test_invalid_provider_response_is_rejected(self):
        provider = FakeProvider(response="not a ModelResponse")
        result = ModelGateway(provider).generate("Bonjour", enabled=True)
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "invalid_provider_response")

    def test_empty_provider_text_is_rejected(self):
        provider = FakeProvider(ModelResponse(" ", "fake-local"))
        result = ModelGateway(provider).generate("Bonjour", enabled=True)
        self.assertFalse(result.ok)
        self.assertEqual(result.error, "empty_provider_response")


if __name__ == "__main__":
    unittest.main()
