package ai.bozer.app

/** Small seam that lets repository behavior be tested without loading native weights. */
interface ConversationModel {
    val isLoaded: Boolean
    fun generate(prompt: String): String
}

/**
 * JNI facade for the bundled llama.cpp runtime.
 * ChatRepository serializes generation requests.
 */
class LocalLlamaEngine : ConversationModel {
    companion object {
        init { System.loadLibrary("bozer_llama") }
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeRelease()

    @Volatile private var loaded = false
    override val isLoaded: Boolean get() = loaded
    @Volatile private var nativeAttempted = false

    @Synchronized
    fun load(modelPath: String) {
        if (loaded) return
        nativeAttempted = true
        check(nativeLoadModel(modelPath, 2048)) {
            "Le moteur n'a pas réussi à charger le modèle."
        }
        loaded = true
    }

    @Synchronized
    override fun generate(prompt: String): String {
        check(loaded) { "Le modèle BOZER n'est pas chargé." }
        // Keep the local model responsive on entry-level Android CPUs. The
        // generation loop also has a native hard cap, so a malformed caller
        // cannot request an unbounded inference session.
        return nativeGenerate(prompt, 128).trim().also {
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
