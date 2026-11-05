package ai.bozer.app

/**
 * JNI facade for the bundled llama.cpp runtime.
 * ChatRepository serializes generation requests.
 */
class LocalLlamaEngine {
    companion object {
        init { System.loadLibrary("bozer_llama") }
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeRelease()

    @Volatile private var loaded = false
    @Volatile private var nativeAttempted = false

    @Synchronized
    fun load(modelPath: String) {
        if (loaded) return
        nativeAttempted = true
        check(nativeLoadModel(modelPath, 1024)) {
            "Le moteur n'a pas réussi à charger le modèle."
        }
        loaded = true
    }

    @Synchronized
    fun generate(prompt: String): String {
        check(loaded) { "Le modèle BOZER n'est pas chargé." }
        // Most conversational replies are short; cap generation to avoid
        // spending CPU and battery on unnecessarily long continuations.
        return nativeGenerate(prompt, 96).trim().also {
            check(it.isNotBlank()) { "Le modèle a renvoyé une réponse vide." }
        }
    }

    @Synchronized
    fun close() {
        if (nativeAttempted) {
            nativeRelease()
            nativeAttempted = false
            loaded = false
        }
    }
}
