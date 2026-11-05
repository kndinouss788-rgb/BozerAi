"""Real public-source adapters for BOZER EYE_SEARCH.

Uses only Python's standard library and fixed Wikimedia HTTPS endpoints.
No API key, third-party package, arbitrary URL, or page crawler is used.
"""
import json
import re
from html import unescape
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .eye_search import SearchResult


_USER_AGENT = "BOZER-EYE-SEARCH/0.1 (public knowledge lookup)"
_MAX_RESPONSE_BYTES = 1_000_000


def _request_json(url, timeout):
    request = Request(url, headers={
        "Accept": "application/json",
        "User-Agent": _USER_AGENT,
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
    """Searches public Wikipedia pages through the MediaWiki Action API."""

    def __init__(self, language="en", timeout=5.0):
        if not re.fullmatch(r"[a-z]{2,3}", language):
            raise ValueError("language must be a short lowercase language code")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.language = language
        self.timeout = timeout

    @property
    def source_name(self):
        return f"Wikipedia ({self.language})"

    def search(self, query, limit):
        if not 1 <= limit <= 10:
            raise ValueError("limit must be between 1 and 10")
        params = urlencode({
            "action": "query",
            "list": "search",
            "srsearch": query,
            "srlimit": limit,
            "srprop": "snippet",
            "format": "json",
            "formatversion": 2,
        })
        endpoint = f"https://{self.language}.wikipedia.org/w/api.php?{params}"
        data = _request_json(endpoint, self.timeout)
        items = data.get("query", {}).get("search", [])
        results = []
        for item in items[:limit]:
            title = _plain_text(item.get("title", ""))
            snippet = _plain_text(item.get("snippet", ""))
            if not title:
                continue
            page = title.replace(" ", "_")
            from urllib.parse import quote
            results.append(SearchResult(
                title=title,
                url=f"https://{self.language}.wikipedia.org/wiki/{quote(page)}",
                snippet=snippet or title,
                source=self.source_name,
            ))
        return results


class WikidataSearchSource:
    """Searches Wikidata's public entity index via wbsearchentities."""

    def __init__(self, language="en", timeout=5.0):
        if not re.fullmatch(r"[a-z]{2,3}", language):
            raise ValueError("language must be a short lowercase language code")
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.language = language
        self.timeout = timeout

    @property
    def source_name(self):
        return f"Wikidata ({self.language})"

    def search(self, query, limit):
        if not 1 <= limit <= 10:
            raise ValueError("limit must be between 1 and 10")
        params = urlencode({
            "action": "wbsearchentities",
            "search": query,
            "language": self.language,
            "uselang": self.language,
            "type": "item",
            "limit": limit,
            "format": "json",
        })
        data = _request_json(f"https://www.wikidata.org/w/api.php?{params}", self.timeout)
        results = []
        for item in data.get("search", [])[:limit]:
            title = _plain_text(item.get("label", ""))
            description = _plain_text(item.get("description", ""))
            url = item.get("concepturi", "")
            if title and description and isinstance(url, str):
                results.append(SearchResult(
                    title=title,
                    url=url,
                    snippet=description,
                    source=self.source_name,
                ))
        return results
