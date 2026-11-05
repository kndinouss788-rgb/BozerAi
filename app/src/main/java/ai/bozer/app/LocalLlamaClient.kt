package ai.bozer.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class LocalLlamaClient(
    private val endpoint: String = "http://127.0.0.1:8080/v1/chat/completions",
    private val model: String = "qwen-local"
) {
    fun complete(history: List<ChatMessage>): String {
        val uri = URI(endpoint)
        require(uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "::1") &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Le moteur local doit rester accessible uniquement depuis cet appareil."
        }
        val messages = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", "Tu es BOZER, un assistant francophone naturel, chaleureux, direct et honnête. Tu réponds dans la langue de l'utilisateur. Tu ne prétends jamais avoir exécuté une action que tu n'as pas exécutée."))
        history.takeLast(20).forEach { item ->
            if (item.role == "user" || item.role == "assistant") {
                messages.put(JSONObject().put("role", item.role).put("content", item.content))
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 700)
            .put("stream", false)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 2500
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            val json = JSONObject(responseText)
            val answer = json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
            if (answer.isBlank()) throw IllegalStateException("Réponse vide du modèle")
            return answer
        } finally {
            connection.disconnect()
        }
    }
}
