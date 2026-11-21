package ai.bozer.app

import android.net.Uri
import android.text.Html
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class InternetSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = "Internet"
)

/**
 * EYE_SEARCH: bounded public-web discovery from the Android app.
 *
 * Uses DuckDuckGo's public HTML results plus Wikipedia's public search API.
 * Search engines index only discoverable public pages; this is not a claim
 * that every page on the Internet can be scanned.
 */
class AndroidPublicSearch(
    private val connectTimeoutMs: Int = 7_000,
    private val readTimeoutMs: Int = 9_000
) {
    fun search(query: String, limit: Int = 8): List<InternetSearchResult> {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "La recherche ne peut pas être vide." }
        require(cleanQuery.length <= 300) { "La recherche est trop longue." }
        require(limit in 1..10) { "Le nombre de résultats doit être compris entre 1 et 10." }

        val collected = mutableListOf<InternetSearchResult>()
        // Providers are independent: one failure must not cancel the other.
        runCatching { searchDuckDuckGo(cleanQuery, limit) }
            .onSuccess(collected::addAll)
            .onFailure { Log.w("BOZER_SEARCH", "DuckDuckGo indisponible", it) }
        if (collected.size < limit) {
            runCatching { searchWikipedia(cleanQuery, minOf(5, limit - collected.size)) }
                .onSuccess(collected::addAll)
                .onFailure { Log.w("BOZER_SEARCH", "Wikipedia indisponible", it) }
        }

        val unique = collected.distinctBy { normalizeUrl(it.url) }.take(limit)
        Log.i("BOZER_SEARCH", "providers=duckduckgo,wikipedia results=" + unique.size)
        return unique
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<InternetSearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val html = requestText(
            "https://html.duckduckgo.com/html/?q=$encoded",
            "text/html,application/xhtml+xml"
        )
        val anchors = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val hrefRegex = Regex("""href\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val classRegex = Regex("""class\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val snippets = Regex(
            """<(?:a|div)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()

        val results = mutableListOf<InternetSearchResult>()
        for (match in anchors.findAll(html)) {
            val attributes = match.groupValues[1]
            val classes = classRegex.find(attributes)?.groupValues?.get(1).orEmpty()
            if (!classes.split(Regex("\\s+")).contains("result__a")) continue
            val rawHref = hrefRegex.find(attributes)?.groupValues?.get(1) ?: continue
            val title = cleanHtml(match.groupValues[2])
            val target = unwrapDuckDuckGoUrl(rawHref)
            if (title.isBlank() || !target.startsWith("https://") || isSearchHost(target)) continue
            val snippet = snippets.getOrNull(results.size).orEmpty()
            results += InternetSearchResult(
                title = title.take(MAX_TITLE_CHARS),
                url = target,
                snippet = snippet.take(MAX_SNIPPET_CHARS),
                source = "DuckDuckGo"
            )
            if (results.size >= limit) break
        }
        return results
    }

    private fun searchWikipedia(query: String, limit: Int): List<InternetSearchResult> {
        if (limit <= 0) return emptyList()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val endpoint = "https://fr.wikipedia.org/w/api.php?action=query&list=search" +
            "&format=json&utf8=1&srlimit=$limit&srsearch=$encoded"
        val root = JSONObject(requestText(endpoint, "application/json"))
        val items = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(items.length(), limit)) {
                val item = items.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val snippet = cleanHtml(item.optString("snippet"))
                if (title.isNotEmpty()) {
                    val articlePath = Uri.encode(title.replace(' ', '_'))
                    add(
                        InternetSearchResult(
                            title = title,
                            url = "https://fr.wikipedia.org/wiki/$articlePath",
                            snippet = snippet.take(MAX_SNIPPET_CHARS),
                            source = "Wikipedia"
                        )
                    )
                }
            }
        }
    }

    private fun requestText(endpoint: String, accept: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "fr,en;q=0.8")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) BOZER/0.3 public-search")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Service de recherche HTTP $status.")
            val stream = connection.inputStream
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IOException("Réponse de recherche trop volumineuse.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanHtml(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun unwrapDuckDuckGoUrl(value: String): String {
        val parsed = Uri.parse(value)
        return parsed.getQueryParameter("uddg") ?: value
    }

    private fun normalizeUrl(value: String): String =
        value.substringBefore('#').trimEnd('/').lowercase()

    private fun isSearchHost(value: String): Boolean =
        Uri.parse(value).host.orEmpty().endsWith("duckduckgo.com")

    companion object {
        private const val MAX_RESPONSE_BYTES = 1_500_000
        private const val MAX_TITLE_CHARS = 180
        private const val MAX_SNIPPET_CHARS = 500
    }
}
