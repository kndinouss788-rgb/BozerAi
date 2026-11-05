package ai.bozer.app

/**
 * JNI facade for the bundled llama.cpp runtime.
 * All inference calls are serialized by ChatRepository.
 */
class LocalLlamaEngine {
    companion object {
        init { System.loadLibrary("bozer_llama") }
    }

    private external fun nativeLoadModel(modelPath: String, contextSize: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeRelease()

    @Volatile private var loaded = false

    @Synchronized
    fun load(modelPath: String) {
        if (loaded) return
        check(nativeLoadModel(modelPath, 2048)) {
            "Le moteur n'a pas réussi à charger le modèle."
        }
        loaded = true
    }

    @Synchronized
    fun generate(prompt: String): String {
        check(loaded) { "Le modèle BOZER n'est pas chargé." }
        return nativeGenerate(prompt, 256).trim().also {
            check(it.isNotBlank()) { "Le modèle a renvoyé une réponse vide." }
        }
    }

    @Synchronized
    fun close() {
        if (loaded) {
            nativeRelease()
            loaded = false
        }
    }
}
