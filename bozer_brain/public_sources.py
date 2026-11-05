"""Public-source adapters for BOZER EYE_SEARCH.

Wikimedia uses public APIs. Brave Search uses its documented Web Search API
and requires a user-supplied subscription token. No HTML search-page scraping.
"""
import json
import os
import re
from html import unescape
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

from .eye_search import EyeSearch, SearchResult


_USER_AGENT = "BOZER-EYE-SEARCH/0.2 (public research)"
_MAX_RESPONSE_BYTES = 1_000_000


def _request_json(url, timeout, headers=None, data=None):
    request = Request(url, data=data, headers={
        "Accept": "application/json",
        "User-Agent": _USER_AGENT,
        **(headers or {}),
    })
    with urlopen(request, timeout=timeout) as response:
        payload = response.read(_MAX_RESPONSE_BYTES + 1)
    if len(payload) > _MAX_RESPONSE_BYTES:
        raise ValueError("public search response too large")
    return json.loads(payload.decode("utf-8"))


def _plain_text(value):
    if not isinstance(value, str):
        return ""
    without_tags = re.sub(r"<[^>]*>", " ", value)
    return " ".join(unescape(without_tags).split())


class WikipediaSearchSource:
    """Searches Wikipedia through the MediaWiki Action API."""

    def __init__(self, language="en", timeout=5.0):
        if not re.fullmatch(r"[a-z]{2,3}", language):
            raise ValueError("language must be a short lowercase language code")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.language, self.timeout = language, timeout

    @property
    def source_name(self):
        return f"Wikipedia ({self.language})"

    def search(self, query, limit):
        if not 1 <= limit <= 10:
            raise ValueError("limit must be between 1 and 10")
        params = urlencode({"action": "query", "list": "search", "srsearch": query,
                            "srlimit": limit, "srprop": "snippet", "format": "json",
                            "formatversion": 2})
        data = _request_json(
            f"https://{self.language}.wikipedia.org/w/api.php?{params}", self.timeout
        )
        results = []
        for item in data.get("query", {}).get("search", [])[:limit]:
            title = _plain_text(item.get("title", ""))
            snippet = _plain_text(item.get("snippet", ""))
            if title:
                results.append(SearchResult(
                    title, f"https://{self.language}.wikipedia.org/wiki/{quote(title.replace(' ', '_'))}",
                    snippet or title, self.source_name
                ))
        return results


class WikidataSearchSource:
    """Searches Wikidata entities using wbsearchentities."""

    def __init__(self, language="en", timeout=5.0):
        if not re.fullmatch(r"[a-z]{2,3}", language):
            raise ValueError("language must be a short lowercase language code")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.language, self.timeout = language, timeout

    @property
    def source_name(self):
        return f"Wikidata ({self.language})"

    def search(self, query, limit):
        if not 1 <= limit <= 10:
            raise ValueError("limit must be between 1 and 10")
        params = urlencode({"action": "wbsearchentities", "search": query,
                            "language": self.language, "uselang": self.language,
                            "type": "item", "limit": limit, "format": "json"})
        data = _request_json(f"https://www.wikidata.org/w/api.php?{params}", self.timeout)
        results = []
        for item in data.get("search", [])[:limit]:
            title = _plain_text(item.get("label", ""))
            description = _plain_text(item.get("description", ""))
            url = item.get("concepturi", "")
            if title and description and isinstance(url, str):
                results.append(SearchResult(title, url, description, self.source_name))
        return results


class BraveSearchSource:
    """Searches the wider public web through Brave's official Web Search API."""

    ENDPOINT = "https://api.search.brave.com/res/v1/web/search"

    def __init__(self, api_key, timeout=8.0, opener=None, country="BF", search_lang="fr"):
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValueError("Brave Search API key is required")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        if not re.fullmatch(r"[A-Z]{2}", country):
            raise ValueError("country must be a two-letter uppercase code")
        if not re.fullmatch(r"[a-z]{2,3}", search_lang):
            raise ValueError("search_lang must be a short lowercase language code")
        self._api_key = api_key.strip()
        self.timeout = timeout
        self._opener = opener or urlopen
        self.country = country
        self.search_lang = search_lang

    @property
    def source_name(self):
        return "Brave Web Search"

    def search(self, query, limit):
        if not isinstance(query, str) or not query.strip():
            raise ValueError("query must not be empty")
        if len(query) > 600 or len(query.split()) > 75:
            raise ValueError("query exceeds Brave Search limits")
        if not 1 <= limit <= 10:
            raise ValueError("limit must be between 1 and 10")
        params = urlencode({"q": query, "count": limit, "country": self.country,
                            "search_lang": self.search_lang})
        request = Request(self.ENDPOINT + "?" + params, headers={
            "Accept": "application/json",
            "X-Subscription-Token": self._api_key,
            "User-Agent": _USER_AGENT,
        })
        with self._opener(request, timeout=self.timeout) as response:
            payload = response.read(_MAX_RESPONSE_BYTES + 1)
        if len(payload) > _MAX_RESPONSE_BYTES:
            raise ValueError("public search response too large")
        data = json.loads(payload.decode("utf-8"))
        web = data.get("web", {}).get("results", [])
        results = []
        for item in web[:limit]:
            title = _plain_text(item.get("title", ""))
            url = item.get("url", "")
            snippet = _plain_text(item.get("description", ""))
            if title and isinstance(url, str) and url.startswith("https://") and snippet:
                results.append(SearchResult(title, url, snippet, self.source_name))
        return results


def eye_search_from_env(environ=None, language="fr", country="BF"):
    """Create a French-first EYE_SEARCH stack; Brave is optional and key-backed."""
    values = os.environ if environ is None else environ
    sources = []
    brave_key = values.get("BOZER_BRAVE_SEARCH_API_KEY", "").strip()
    if brave_key:
        sources.append(BraveSearchSource(
            brave_key, country=country, search_lang=language
        ))
    sources.extend((
        WikipediaSearchSource(language=language),
        WikidataSearchSource(language=language),
    ))
    return EyeSearch(sources)
