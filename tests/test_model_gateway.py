import json, unittest
from unittest.mock import patch
from bozer_brain.model_gateway import ModelGateway, ModelRequest, ModelResponse, OpenAICompatibleProvider, OpenAIResponsesProvider, openai_provider_from_env

class FakeProvider:
    provider_name = "fake-local"
    def __init__(self, response=None, error=False): self.response=response or ModelResponse(" Réponse test ", "fake-local", "unit"); self.error=error; self.requests=[]
    def generate(self, request):
        self.requests.append(request)
        if self.error: raise RuntimeError("sensitive provider detail")
        return self.response

class FakeResponse:
    def __init__(self, payload): self.payload=payload
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self, size=-1): return self.payload[:size]

class ModelGatewayTests(unittest.TestCase):
    def test_explicit_opt_in_required(self):
        p=FakeProvider(); r=ModelGateway(p).generate("Bonjour"); self.assertEqual(r.error,"provider_use_not_enabled"); self.assertEqual(p.requests,[])
    def test_missing_provider(self): self.assertEqual(ModelGateway().generate("Bonjour",enabled=True).error,"provider_not_configured")
    def test_bounded_success(self):
        p=FakeProvider(ModelResponse("  réponse longue  ","local","small")); r=ModelGateway(p).generate("Bonjour",enabled=True,max_output_chars=8)
        self.assertTrue(r.ok); self.assertEqual(r.text,"réponse")
    def test_provider_error_is_sanitized(self): self.assertEqual(ModelGateway(FakeProvider(error=True)).generate("x",enabled=True).error,"provider_error")
    def test_invalid_inputs(self):
        g=ModelGateway(FakeProvider()); self.assertEqual(g.generate(" ").error,"empty_prompt"); self.assertEqual(g.generate("x",enabled=True,max_output_chars=0).error,"invalid_output_limit")
    def test_invalid_provider_response(self): self.assertEqual(ModelGateway(FakeProvider(response="bad")).generate("x",enabled=True).error,"invalid_provider_response")
    def test_empty_provider_text(self): self.assertEqual(ModelGateway(FakeProvider(ModelResponse(" ","fake"))).generate("x",enabled=True).error,"empty_provider_response")
    @patch("bozer_brain.model_gateway.urlopen")
    def test_chat_completions_adapter(self,mock):
        mock.return_value=FakeResponse(json.dumps({"model":"demo","choices":[{"message":{"content":"Salut"}}]}).encode())
        p=OpenAICompatibleProvider("secret","demo"); r=p.generate(ModelRequest("Bonjour")); req=mock.call_args.args[0]
        self.assertEqual(req.full_url,"https://api.openai.com/v1/chat/completions"); self.assertEqual(req.get_header("Authorization"),"Bearer secret"); self.assertEqual(r.text,"Salut")
    @patch("bozer_brain.model_gateway.urlopen")
    def test_native_responses_api_adapter(self,mock):
        mock.return_value=FakeResponse(json.dumps({"model":"gpt-model","output":[{"type":"message","content":[{"type":"output_text","text":"Réponse réelle"}]}]}).encode())
        p=OpenAIResponsesProvider("secret","gpt-model"); r=p.generate(ModelRequest("Présente-toi")); req=mock.call_args.args[0]; body=json.loads(req.data.decode())
        self.assertEqual(req.full_url,"https://api.openai.com/v1/responses"); self.assertEqual(req.get_header("Authorization"),"Bearer secret")
        self.assertFalse(body["store"]); self.assertEqual(body["input"],"Présente-toi"); self.assertEqual(r.text,"Réponse réelle")
    def test_https_required(self):
        with self.assertRaises(ValueError): OpenAIResponsesProvider("key","model","http://example.com/v1")
    def test_env_defaults_to_responses_api(self):
        self.assertIsNone(openai_provider_from_env({})); p=openai_provider_from_env({"BOZER_MODEL_API_KEY":"secret","BOZER_MODEL_NAME":"gpt-model"}); self.assertIsInstance(p,OpenAIResponsesProvider)
    def test_env_can_select_compatible_api(self):
        p=openai_provider_from_env({"BOZER_MODEL_API_KEY":"secret","BOZER_MODEL_NAME":"demo","BOZER_MODEL_API_STYLE":"chat_completions"}); self.assertIsInstance(p,OpenAICompatibleProvider)

if __name__ == "__main__": unittest.main()
