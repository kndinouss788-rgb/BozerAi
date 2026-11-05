import unittest
from unittest.mock import patch

from bozer_brain.public_sources import WikipediaSearchSource, WikidataSearchSource


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, size=-1):
        return self.payload


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
        self.assertIn("User-Agent", mocked.call_args.args[0].headers)

    @patch("bozer_brain.public_sources.urlopen")
    def test_wikidata_maps_entity_results(self, mocked):
        mocked.return_value = FakeResponse(
            b'{"search":[{"label":"Ouagadougou","description":"capital city","concepturi":"http://www.wikidata.org/entity/Q3777"}]}'
        )
        results = WikidataSearchSource(language="fr").search("Ouagadougou", 1)
        self.assertEqual(results[0].title, "Ouagadougou")
        self.assertEqual(results[0].snippet, "capital city")
        self.assertEqual(results[0].source, "Wikidata (fr)")

    def test_invalid_language_and_limit_rejected(self):
        with self.assertRaises(ValueError):
            WikipediaSearchSource(language="en.wikipedia.org")
        with self.assertRaises(ValueError):
            WikidataSearchSource().search("q", 0)

    @patch("bozer_brain.public_sources.urlopen")
    def test_response_size_is_bounded(self, mocked):
        mocked.return_value = FakeResponse(b"x" * 1_000_001)
        with self.assertRaises(ValueError):
            WikipediaSearchSource().search("q", 1)


if __name__ == "__main__":
    unittest.main()
