package ai.bozer.app

import android.net.Uri
import android.text.Html
import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
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
    val source: String = "Source publique",
    val publishedAt: String? = null
)

/** Injectable boundary for real public web searches performed by the Android process. */
interface PublicSearchClient {
    fun search(query: String, limit: Int = 3): List<InternetSearchResult>
}

/**
 * Multi-source public research without a provider API key:
 * - Bing public RSS results (web-wide index)
 * - French Wikipedia API results (encyclopedic fallback)
 *
 * Either source may fail independently. Results are labelled with their
 * provenance and publication date when the source actually supplies one.
 */
class AndroidPublicSearch(
    private val connectTimeoutMs: Int = 7_000,
    private val readTimeoutMs: Int = 9_000
) : PublicSearchClient {

    override fun search(query: String, limit: Int): List<InternetSearchResult> {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "La recherche ne peut pas être vide." }
        require(cleanQuery.length <= 300) { "La recherche est trop longue." }
        require(limit in 1..5) { "Le nombre de résultats doit être compris entre 1 et 5." }

        val results = mutableListOf<InternetSearchResult>()
        val failures = mutableListOf<String>()

        try {
            results += searchBingRss(cleanQuery, minOf(limit, 4))
        } catch (error: Exception) {
            failures += "Bing: " + (error.message ?: "source indisponible")
        }

        try {
            results += searchWikipedia(cleanQuery, minOf(limit, 3))
        } catch (error: Exception) {
            failures += "Wikipédia: " + (error.message ?: "source indisponible")
        }

        val unique = results.distinctBy { canonicalUrl(it.url) }.take(limit)
        if (unique.isEmpty() && failures.size == 2) {
            throw IOException("Aucune source de recherche n'a répondu. " + failures.joinToString(" ; "))
        }
        return unique
    }

    private fun searchBingRss(query: String, limit: Int): List<InternetSearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val endpoint = URL(
            "https://www.bing.com/search?format=rss&count=$limit&setlang=fr&q=$encoded"
        )
        val connection = open(endpoint, "application/rss+xml, application/xml;q=0.9, */*;q=0.5")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("réponse HTTP ${connection.responseCode}")
            }
            val bytes = readBounded(connection, MAX_RESPONSE_BYTES)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(bytes.inputStream(), "UTF-8")

            val items = mutableListOf<InternetSearchResult>()
            var insideItem = false
            var title = ""
            var link = ""
            var description = ""
            var publishedAt: String? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "item" -> {
                                insideItem = true
                                title = ""
                                link = ""
                                description = ""
                                publishedAt = null
                            }
                            "title" -> if (insideItem) title = parser.nextText().cleanHtml()
                            "link" -> if (insideItem) link = parser.nextText().trim()
                            "description" -> if (insideItem) {
                                description = parser.nextText().cleanHtml()
                            }
                            "pubdate" -> if (insideItem) publishedAt = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("item", ignoreCase = true)) {
                        insideItem = false
                        if (title.isNotBlank() && isHttpUrl(link)) {
                            items += InternetSearchResult(
                                title = title.take(MAX_TITLE_CHARS),
                                url = link,
                                snippet = description.take(MAX_SNIPPET_CHARS),
                                source = "Bing Search",
                                publishedAt = publishedAt?.takeIf(String::isNotBlank)
                            )
                        }
                    }
                }
                event = parser.next()
            }
            return items
        } finally {
            connection.disconnect()
        }
    }

    private fun searchWikipedia(query: String, limit: Int): List<InternetSearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val endpoint = URL(
            "https://fr.wikipedia.org/w/api.php?action=query&list=search" +
                "&format=json&utf8=1&srlimit=$limit&srsearch=$encoded"
        )
        val connection = open(endpoint, "application/json")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("réponse HTTP ${connection.responseCode}")
            }
            val bytes = readBounded(connection, MAX_RESPONSE_BYTES)
            val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
            val items = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            return buildList {
                for (index in 0 until minOf(items.length(), limit)) {
                    val item = items.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val snippet = item.optString("snippet").cleanHtml()
                    if (title.isNotEmpty() && snippet.isNotEmpty()) {
                        val articlePath = Uri.encode(title.replace(' ', '_'))
                        add(
                            InternetSearchResult(
                                title = title.take(MAX_TITLE_CHARS),
                                url = "https://fr.wikipedia.org/wiki/$articlePath",
                                snippet = snippet.take(MAX_SNIPPET_CHARS),
                                source = "Wikipédia"
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: URL, accept: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7")
            setRequestProperty("User-Agent", "BOZER-Android/1.0 (public research)")
        }

    private fun readBounded(connection: HttpURLConnection, maximumBytes: Int): ByteArray {
        val announced = connection.contentLengthLong
        if (announced > maximumBytes) throw IOException("réponse de recherche trop volumineuse")
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maximumBytes) throw IOException("réponse de recherche trop volumineuse")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun String.cleanHtml(): String = Html.fromHtml(
        this,
        Html.FROM_HTML_MODE_LEGACY
    ).toString().replace(Regex("\\s+"), " ").trim()

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")

    private fun canonicalUrl(value: String): String = value.trim().trimEnd('/').lowercase()

    companion object {
        private const val MAX_RESPONSE_BYTES = 1_000_000
        private const val MAX_TITLE_CHARS = 180
        private const val MAX_SNIPPET_CHARS = 600
    }
}

/** Explicit name for the multi-source implementation used by the brain layer. */
typealias MultiSourcePublicSearch = AndroidPublicSearch
