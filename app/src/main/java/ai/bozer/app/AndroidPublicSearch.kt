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

data class InternetSearchResult(val title: String, val url: String, val snippet: String)

/** Real, bounded Wikipedia search executed by the Android process. */
class AndroidPublicSearch(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) {
    fun search(query: String, limit: Int = 3): List<InternetSearchResult> {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "La recherche ne peut pas être vide." }
        require(cleanQuery.length <= 300) { "La recherche est trop longue." }
        require(limit in 1..5) { "Le nombre de résultats doit être compris entre 1 et 5." }

        val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
        val endpoint = URL(
            "https://fr.wikipedia.org/w/api.php?action=query&list=search" +
                "&format=json&utf8=1&srlimit=$limit&srsearch=$encoded"
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BOZER-Android/0.2 (public research)")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Le service de recherche a répondu HTTP $status.")
            val bytes = connection.inputStream.use { input ->
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
            val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
            val items = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val results = buildList {
                for (index in 0 until minOf(items.length(), limit)) {
                    val item = items.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    val snippet = Html.fromHtml(
                        item.optString("snippet"),
                        Html.FROM_HTML_MODE_LEGACY
                    ).toString().replace(Regex("\\s+"), " ").trim()
                    if (title.isNotEmpty() && snippet.isNotEmpty()) {
                        val articleUrl = "https://fr.wikipedia.org/wiki/\${Uri.encode(title.replace(' ', '_'))}"
                        add(InternetSearchResult(title, articleUrl, snippet.take(MAX_SNIPPET_CHARS)))
                    }
                }
            }
            Log.i("BOZER_SEARCH", "wikipedia_results=\${results.size}")
            return results
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1_000_000
        private const val MAX_SNIPPET_CHARS = 420
    }
}
