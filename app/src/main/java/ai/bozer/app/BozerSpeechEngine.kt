package ai.bozer.app

/** Speech output boundary with sentence callbacks for synchronized captions. */
interface BozerSpeechEngine {
    fun speak(
        text: String,
        utteranceId: String = "bozer_reply",
        onSegmentStarted: (start: Int, endExclusive: Int) -> Unit = { _, _ -> },
        onCompleted: () -> Unit = {}
    )
    fun stop(utteranceId: String? = null)
    fun isReady(): Boolean
    fun release()
}
