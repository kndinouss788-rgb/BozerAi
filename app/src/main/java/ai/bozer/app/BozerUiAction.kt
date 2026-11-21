package ai.bozer.app

/** A small, explicit vocabulary for controlling the currently visible app UI. */
sealed interface BozerUiAction {
    data class TapText(val label: String) : BozerUiAction
    data class EnterText(val value: String) : BozerUiAction
    data object ScrollForward : BozerUiAction
    data object ScrollBackward : BozerUiAction
    data object PressBack : BozerUiAction
    data object PressHome : BozerUiAction
}

/**
 * Recognises only direct, user-authored UI instructions.
 * A model-generated plan may use the same typed actions, but it must still
 * pass through the app's permission and confirmation gates.
 */
object BozerUiActionParser {
    fun parse(input: String): List<BozerUiAction>? {
        val chunks = input.trim().split(Regex("\\s+ puis \\s+"), ignoreCase = true)
        if (chunks.isEmpty() || chunks.size > MAX_STEPS) return null
        val actions = chunks.map { parseOne(it.trim()) ?: return null }
        return actions
    }

    private fun parseOne(raw: String): BozerUiAction? {
        val normalized = normalize(raw)
        when (normalized) {
            "fais defiler vers le bas", "defile vers le bas", "scroll vers le bas", "scroll down", "descends la page" ->
                return BozerUiAction.ScrollForward
            "fais defiler vers le haut", "defile vers le haut", "scroll vers le haut", "scroll up", "remonte la page" ->
                return BozerUiAction.ScrollBackward
            "reviens en arriere", "retour", "retourne en arriere", "appuie sur retour", "reviens a la page precedente" ->
                return BozerUiAction.PressBack
            "retourne a l accueil", "va a l accueil", "affiche l ecran d accueil", "ouvre l ecran d accueil" ->
                return BozerUiAction.PressHome
        }

        Regex("^(?:clique|appuie|touche|selectionne|choisis)\\s+(?:sur\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?')
            ?.takeIf { it.isNotBlank() && it.length <= 100 }
            ?.let { return BozerUiAction.TapText(it) }

        Regex("^(?:tape|ecris|saisis|entre)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!')
            ?.takeIf { it.isNotBlank() && it.length <= 500 }
            ?.let { return BozerUiAction.EnterText(it) }

        return null
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("[-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val MAX_STEPS = 8
}
