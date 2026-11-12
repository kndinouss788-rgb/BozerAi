import unittest

from bozer_brain.model_gateway import ModelRequest, ModelResponse
from bozer_brain.provider_router import (
    ProviderCandidate,
    ProviderCostClass,
    ProviderRouter,
)


class FakeProvider:
    def __init__(self, name, text=None, error=False, invalid=False):
        self.provider_name = name
        self.text = text
        self.error = error
        self.invalid = invalid
        self.calls = 0

    def generate(self, request):
        self.calls += 1
        if self.error:
            raise RuntimeError("secret provider failure")
        if self.invalid:
            return {"text": "not a ModelResponse"}
        return ModelResponse(self.text or "", self.provider_name, self.provider_name)


class ProviderRouterTests(unittest.TestCase):
    def test_local_provider_is_used_first_and_remote_is_not_called(self):
        local = FakeProvider("local", "Réponse locale")
        remote = FakeProvider("remote", "Réponse distante")
        router = ProviderRouter((
            ProviderCandidate(local, ProviderCostClass.LOCAL),
            ProviderCandidate(remote, ProviderCostClass.METERED),
        ))
        result = router.generate(ModelRequest("Bonjour"))
        self.assertEqual(result.provider_name, "local")
        self.assertEqual(local.calls, 1)
        self.assertEqual(remote.calls, 0)

    def test_metered_fallback_is_disabled_by_default(self):
        local = FakeProvider("local", error=True)
        remote = FakeProvider("remote", "Réponse distante")
        router = ProviderRouter((
            ProviderCandidate(local, ProviderCostClass.LOCAL),
            ProviderCandidate(remote, ProviderCostClass.METERED),
        ))
        with self.assertRaisesRegex(RuntimeError, "no_eligible_provider_succeeded"):
            router.generate(ModelRequest("Bonjour"))
        self.assertEqual(local.calls, 1)
        self.assertEqual(remote.calls, 0)

    def test_metered_fallback_requires_explicit_configuration(self):
        local = FakeProvider("local", error=True)
        remote = FakeProvider("remote", "Réponse distante")
        router = ProviderRouter(
            (
                ProviderCandidate(local, ProviderCostClass.LOCAL),
                ProviderCandidate(remote, ProviderCostClass.METERED),
            ),
            allow_metered_fallback=True,
            max_metered_attempts=1,
        )
        result = router.generate(ModelRequest("Bonjour"))
        self.assertEqual(result.provider_name, "remote")
        self.assertEqual(remote.calls, 1)

    def test_metered_attempt_limit_is_respected(self):
        local = FakeProvider("local", error=True)
        first_remote = FakeProvider("remote-1", error=True)
        second_remote = FakeProvider("remote-2", "Ne doit pas être appelé")
        router = ProviderRouter(
            (
                ProviderCandidate(local, ProviderCostClass.LOCAL),
                ProviderCandidate(first_remote, ProviderCostClass.METERED),
                ProviderCandidate(second_remote, ProviderCostClass.METERED),
            ),
            allow_metered_fallback=True,
            max_metered_attempts=1,
        )
        with self.assertRaisesRegex(RuntimeError, "no_eligible_provider_succeeded"):
            router.generate(ModelRequest("Bonjour"))
        self.assertEqual(first_remote.calls, 1)
        self.assertEqual(second_remote.calls, 0)

    def test_invalid_provider_response_falls_through_safely(self):
        invalid = FakeProvider("invalid", invalid=True)
        remote = FakeProvider("remote", "Réponse distante")
        router = ProviderRouter(
            (
                ProviderCandidate(invalid, ProviderCostClass.LOCAL),
                ProviderCandidate(remote, ProviderCostClass.METERED),
            ),
            allow_metered_fallback=True,
        )
        result = router.generate(ModelRequest("Bonjour"))
        self.assertEqual(result.provider_name, "remote")

    def test_invalid_configuration_is_rejected(self):
        with self.assertRaises(ValueError):
            ProviderRouter(())
        with self.assertRaises(ValueError):
            ProviderRouter(
                (ProviderCandidate(FakeProvider("local", "ok"), ProviderCostClass.LOCAL),),
                max_metered_attempts=3,
            )

    def test_empty_prompt_is_rejected_before_calling_provider(self):
        local = FakeProvider("local", "Réponse")
        router = ProviderRouter((
            ProviderCandidate(local, ProviderCostClass.LOCAL),
        ))
        with self.assertRaises(ValueError):
            router.generate(ModelRequest(" "))
        self.assertEqual(local.calls, 0)


if __name__ == "__main__":
    unittest.main()
