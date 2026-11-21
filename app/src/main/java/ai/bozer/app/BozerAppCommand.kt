package ai.bozer.app

import java.text.Normalizer
import java.util.Locale

/**
 * Voice-addressable controls owned by BOZER itself.
 *
 * These commands are deliberately separated from Android intents so the UI
 * remains the single source of truth for BOZER's own settings.
 */
sealed interface BozerAppCommand {
    data object OpenSettings : BozerAppCommand
    data object OpenAccessibilitySettings : BozerAppCommand
    data class SetSilentMode(val enabled: Boolean) : BozerAppCommand
    data class SetAnimations(val enabled: Boolean) : BozerAppCommand
    data class SetSoundEffects(val enabled: Boolean) : BozerAppCommand
    data class SetFloatingBubble(val enabled: Boolean) : BozerAppCommand
    data class SetTheme(val theme: BozerTheme) : BozerAppCommand
    data object CycleTheme : BozerAppCommand
    data object OpenTikTokForRecording : BozerAppCommand
    data object OpenVideoCamera : BozerAppCommand
}

enum class BozerTheme(val label: String) {
    PEARL("Nacré"),
    GLACIER("Glacier"),
    SAGE("Sauge");

    fun next(): BozerTheme = when (this) {
        PEARL -> GLACIER
        GLACIER -> SAGE
        SAGE -> PEARL
    }

    companion object {
        fun fromStored(value: String?): BozerTheme =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PEARL
    }
}

data class BozerThemePalette(
    val background: Long,
    val gradientStart: Long,
    val gradientMiddle: Long,
    val gradientEnd: Long,
    val accent: Long,
    val secondary: Long,
    val surface: Long
) {
    companion object {
        fun forTheme(theme: BozerTheme): BozerThemePalette = when (theme) {
            BozerTheme.PEARL -> BozerThemePalette(
                0xFFF4F6F3, 0xFFF5F7F4, 0xFFE9EEEB, 0xFFF8F9F7,
                0xFF5A7D70, 0xFF8BA99B, 0xFFF8FAF8
            )
            BozerTheme.GLACIER -> BozerThemePalette(
                0xFFF1F5F8, 0xFFF3F8FC, 0xFFE5EDF3, 0xFFF9FBFD,
                0xFF56778D, 0xFF91AFC0, 0xFFF7FAFC
            )
            BozerTheme.SAGE -> BozerThemePalette(
                0xFFF2F6F0, 0xFFF4F8F1, 0xFFE5EEE3, 0xFFFAFBF7,
                0xFF5F7E63, 0xFF9AB49A, 0xFFF8FAF6
            )
        }
    }
}

/** Recognises only explicit BOZER-owned controls and camera-launch requests. */
object BozerAppCommandParser {
    fun parse(input: String): BozerAppCommand? {
        val text = normalize(input)
            .removePrefix("bozer, ")
            .removePrefix("bozer ")
            .trim()
        if (text.isBlank()) return null

        if (text in setOf(
                "ouvre les parametres d accessibilite", "ouvre les reglages d accessibilite",
                "ouvre les parametres accessibilite", "configure bozer action",
                "configure l automatisation des applications"
            )
        ) return BozerAppCommand.OpenAccessibilitySettings

        if (text in setOf(
                "ouvre tes parametres", "affiche tes parametres",
                "ouvre les parametres de bozer", "ouvre les parametres bozer",
                "ouvre les reglages de bozer", "ouvre les reglages bozer",
                "va dans tes parametres", "va dans les parametres de bozer"
            )
        ) return BozerAppCommand.OpenSettings

        val startsAsAction = text.startsWith("ouvre ") || text.startsWith("lance ") ||
            text.startsWith("demarre ") || text.startsWith("filme ") ||
            text.startsWith("enregistre ")
        if (startsAsAction && text.contains("tiktok") &&
            containsAny(text, "filme", "filmer", "enregistre", "enregistrement", "camera", "video")
        ) return BozerAppCommand.OpenTikTokForRecording

        if (text in setOf(
                "filme moi", "enregistre une video", "lance un enregistrement video",
                "demarre un enregistrement video", "ouvre la camera video",
                "ouvre la camera en mode video", "ouvre l appareil photo en mode video",
                "lance la camera en mode video", "ouvre la camera pour filmer"
            )
        ) return BozerAppCommand.OpenVideoCamera

        if (text in setOf(
                "passe en mode silencieux", "active le mode silencieux",
                "active mode silencieux", "mets toi en mode silencieux",
                "mets toi en silencieux", "sois silencieux", "silence"
            )
        ) return BozerAppCommand.SetSilentMode(true)

        if (text in setOf(
                "quitte le mode silencieux", "desactive le mode silencieux",
                "desactive mode silencieux", "repasse en mode normal",
                "reactive ta voix", "reactive la voix"
            )
        ) return BozerAppCommand.SetSilentMode(false)

        if (text in setOf("change de theme", "change le theme", "theme suivant", "change ton theme")) {
            return BozerAppCommand.CycleTheme
        }

        val requestedTheme = when {
            text.endsWith("theme nacre") || text == "theme nacre" || text == "passe au theme nacre" -> BozerTheme.PEARL
            text.endsWith("theme glacier") || text == "theme glacier" || text == "passe au theme glacier" -> BozerTheme.GLACIER
            text.endsWith("theme sauge") || text == "theme sauge" || text == "passe au theme sauge" -> BozerTheme.SAGE
            else -> null
        }
        if (requestedTheme != null) return BozerAppCommand.SetTheme(requestedTheme)

        if (text in setOf("active les animations", "active les animations fluides", "reactive les animations")) {
            return BozerAppCommand.SetAnimations(true)
        }
        if (text in setOf("desactive les animations", "desactive les animations fluides", "arrete les animations")) {
            return BozerAppCommand.SetAnimations(false)
        }

        if (text in setOf("active les effets sonores", "active les sons", "reactive les sons")) {
            return BozerAppCommand.SetSoundEffects(true)
        }
        if (text in setOf("desactive les effets sonores", "desactive les sons", "coupe les sons")) {
            return BozerAppCommand.SetSoundEffects(false)
        }

        if (text in setOf("active la bulle flottante", "affiche la bulle flottante", "active la bulle bozer")) {
            return BozerAppCommand.SetFloatingBubble(true)
        }
        if (text in setOf("desactive la bulle flottante", "masque la bulle flottante", "desactive la bulle bozer")) {
            return BozerAppCommand.SetFloatingBubble(false)
        }

        return null
    }

    private fun containsAny(value: String, vararg terms: String): Boolean =
        terms.any { term -> value.contains(term) }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("[-_]"), " ")
            .replace(Regex("[.!?]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
