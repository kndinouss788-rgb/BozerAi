package ai.bozer.app

/**
 * Splits long spoken responses into sentence/word-sized utterances.
 * Whitespace is normalized once; no text is silently truncated.
 */
internal object BozerSpeechText {
    const val DEFAULT_MAX_CHARACTERS = 280

    fun chunks(text: String, maxCharacters: Int = DEFAULT_MAX_CHARACTERS): List<String> {
        require(maxCharacters >= 20) { "maxCharacters must be at least 20" }
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()

        val sentences = normalized.split(Regex("(?<=[.!?…])\\s+"))
        val result = mutableListOf<String>()
        var current = ""

        fun flush() {
            if (current.isNotEmpty()) {
                result += current
                current = ""
            }
        }

        for (sentence in sentences) {
            if (sentence.length <= maxCharacters) {
                current = when {
                    current.isEmpty() -> sentence
                    current.length + 1 + sentence.length <= maxCharacters -> current + " " + sentence
                    else -> {
                        flush()
                        sentence
                    }
                }
                continue
            }

            flush()
            var fragment = ""
            for (word in sentence.split(' ')) {
                if (word.length > maxCharacters) {
                    if (fragment.isNotEmpty()) {
                        result += fragment
                        fragment = ""
                    }
                    var offset = 0
                    while (offset + maxCharacters < word.length) {
                        result += word.substring(offset, offset + maxCharacters)
                        offset += maxCharacters
                    }
                    fragment = word.substring(offset)
                } else if (fragment.isEmpty()) {
                    fragment = word
                } else if (fragment.length + 1 + word.length <= maxCharacters) {
                    fragment += " " + word
                } else {
                    result += fragment
                    fragment = word
                }
            }
            if (fragment.isNotEmpty()) current = fragment
        }
        flush()
        return result
    }
}
