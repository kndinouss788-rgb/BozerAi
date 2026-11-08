package ai.bozer.app

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calls BOZER's own gateway. Provider keys stay on the server and are never
 * packaged in the Android APK.
 */
class RemoteBrainClient(
    private val endpoint: String,
    context: Context
) {
    private val appContext = context.applicationContext
    val isConfigured: Boolean get() = endpoint.startsWith("https://")

    suspend fun reply(history: List<ChatMessage>, extraContext: String = ""): String = withContext(Dispatchers.IO) {
        check(isConfigured) { "La passerelle IA distante de BOZER n'est pas configurée." }
        val clientId = installId()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put(
                "content",
                "Tu es BOZER, assistant francophone naturel, chaleureux et honnête. " +
                    "Réponds directement, tiens compte du contexte. " + extraContext
            ))
        history.takeLast(12).forEach { item ->
            if (item.role == "user" || item.role == "assistant") {
                messages.put(JSONObject().put("role", item.role).put("content", item.content.take(6000)))
            }
        }
        val payload = JSONObject()
            .put("messages", messages)
            .put("max_tokens", 700)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 75_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Bozer-Client", clientId)
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Passerelle BOZER indisponible (HTTP $status).")
            }
            val answer = JSONObject(responseText).optString("reply").trim()
            check(answer.isNotBlank()) { "Le cerveau distant a renvoyé une réponse vide." }
            answer
        } finally {
            connection.disconnect()
        }
    }

    private fun installId(): String {
        val preferences = appContext.getSharedPreferences("bozer_remote", Context.MODE_PRIVATE)
        val existing = preferences.getString("install_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        preferences.edit().putString("install_id", created).apply()
        return created
    }
}
