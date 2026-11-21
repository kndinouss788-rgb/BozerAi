package ai.bozer.app

/**
 * Splits long spoken responses into sentence/word-sized utterances.
 * Whitespace is normalized once; no text is silently truncated.
 */
internal object BozerSpeechText {
    const val DEFAULT_MAX_CHARACTERS = 280

    /**
     * Sentence-sized units retain exact source offsets for synchronized captions.
     */
    data class Segment(val text: String, val start: Int, val endExclusive: Int)

    fun segments(text: String, maxCharacters: Int = 220): List<Segment> {
        require(maxCharacters >= 20) { "maxCharacters must be at least 20" }
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Segment>()

        fun appendRange(rawStart: Int, rawEnd: Int) {
            var start = rawStart.coerceIn(0, text.length)
            val end = rawEnd.coerceIn(start, text.length)
            while (start < end && text[start].isWhitespace()) start++
            var finish = end
            while (finish > start && text[finish - 1].isWhitespace()) finish--
            while (start < finish) {
                val limit = minOf(finish, start + maxCharacters)
                val previousSpace = if (limit == finish) -1 else text.lastIndexOf(' ', limit)
                val cut = when {
                    limit == finish -> finish
                    previousSpace > start -> previousSpace
                    else -> limit // A single long token is split without losing source offsets.
                }
                var segmentEnd = cut.coerceIn(start + 1, finish)
                while (segmentEnd > start && text[segmentEnd - 1].isWhitespace()) segmentEnd--
                if (segmentEnd > start) result += Segment(text.substring(start, segmentEnd), start, segmentEnd)
                start = cut.coerceIn(start + 1, finish)
                while (start < finish && text[start].isWhitespace()) start++
            }
        }

        var sentenceStart = 0
        Regex("(?<=[.!?…])\\s+|\\n+").findAll(text).forEach { separator ->
            appendRange(sentenceStart, separator.range.first)
            sentenceStart = separator.range.last + 1
        }
        appendRange(sentenceStart, text.length)
        return result
    }

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
