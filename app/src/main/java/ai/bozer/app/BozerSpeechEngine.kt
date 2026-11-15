package ai.bozer.app

/**
 * Presentation-only speech output boundary.
 *
 * The TTS engine may pronounce a response selected by BOZER NEURAL, but it
 * cannot change reasoning, memory, permissions, policy, or action results.
 */
interface BozerSpeechEngine {
    fun speak(text: String, utteranceId: String = "bozer_reply")
    fun stop(utteranceId: String? = null)
    fun isReady(): Boolean
    fun release()
}
