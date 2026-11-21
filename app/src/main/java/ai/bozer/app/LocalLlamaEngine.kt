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
    val isLoaded: Boolean get() = loaded

    @Synchronized
    fun load(modelPath: String, contextSize: Int = 2048) {
        if (loaded) return
        require(contextSize in 1024..2048) { "La taille du contexte local doit être comprise entre 1 024 et 2 048 tokens." }
        nativeAttempted = true
        check(nativeLoadModel(modelPath, contextSize)) {
            "Le moteur n'a pas réussi à charger le modèle."
        }
        loaded = true
    }

    @Synchronized
    fun generate(prompt: String): String {
        check(loaded) { "Le modèle BOZER n'est pas chargé." }
        // Allow fuller conversational answers instead of cutting every reply
        // at an overly restrictive 96-token limit.
        return nativeGenerate(prompt, 192).trim().also {
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
