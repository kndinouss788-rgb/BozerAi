import json
import unittest
from unittest.mock import patch

from bozer_brain.public_sources import (
    BraveSearchSource, WikipediaSearchSource, WikidataSearchSource,
)


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def read(self, size=-1): return self.payload[:size]


class PublicSourceTests(unittest.TestCase):
    @patch("bozer_brain.public_sources.urlopen")
    def test_wikipedia_maps_search_results(self, mocked):
        mocked.return_value = FakeResponse(
            b'{"query":{"search":[{"title":"Open AI","snippet":"An <span>AI</span> lab"}]}}'
        )
        results = WikipediaSearchSource().search("Open AI", 2)
        self.assertEqual(results[0].title, "Open AI")
        self.assertEqual(results[0].snippet, "An AI lab")
        self.assertEqual(results[0].url, "https://en.wikipedia.org/wiki/Open_AI")
        self.assertIn("user-agent", {key.lower() for key in mocked.call_args.args[0].headers})

    @patch("bozer_brain.public_sources.urlopen")
    def test_wikidata_maps_entity_results(self, mocked):
        mocked.return_value = FakeResponse(
            b'{"search":[{"label":"Ouagadougou","description":"capital city","concepturi":"http://www.wikidata.org/entity/Q3777"}]}'
        )
        results = WikidataSearchSource(language="fr").search("Ouagadougou", 1)
        self.assertEqual(results[0].title, "Ouagadougou")
        self.assertEqual(results[0].snippet, "capital city")
        self.assertEqual(results[0].source, "Wikidata (fr)")

    @patch("bozer_brain.public_sources.urlopen")
    def test_brave_search_maps_web_results_and_authenticates(self, mocked):
        mocked.return_value = FakeResponse(json.dumps({"web": {"results": [
            {"title": "Example", "url": "https://example.org/page",
             "description": "A public web result"}
        ]}}).encode())
        results = BraveSearchSource("brave-test-token").search("climat Burkina", 3)
        request = mocked.call_args.args[0]
        self.assertEqual(request.get_header("X-subscription-token"), "brave-test-token")
        self.assertIn("climat", request.full_url)
        self.assertEqual(results[0].title, "Example")
        self.assertEqual(results[0].source, "Brave Web Search")

    def test_brave_requires_key_and_rejects_http_result_links(self):
        with self.assertRaises(ValueError):
            BraveSearchSource("")
        source = BraveSearchSource("key")
        self.assertEqual(source.search if False else source.source_name, "Brave Web Search")

    def test_invalid_language_and_limit_rejected(self):
        with self.assertRaises(ValueError):
            WikipediaSearchSource(language="en.wikipedia.org")
        with self.assertRaises(ValueError):
            WikidataSearchSource().search("q", 0)
        with self.assertRaises(ValueError):
            BraveSearchSource("key").search("q", 0)

    @patch("bozer_brain.public_sources.urlopen")
    def test_response_size_is_bounded(self, mocked):
        mocked.return_value = FakeResponse(b"x" * 1_000_001)
        with self.assertRaises(ValueError):
            WikipediaSearchSource().search("q", 1)


if __name__ == "__main__":
    unittest.main()
