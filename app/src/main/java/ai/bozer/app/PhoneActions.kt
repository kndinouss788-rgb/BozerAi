package ai.bozer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.net.Uri
import android.content.pm.PackageManager

sealed interface PhoneAction {
    data class OpenApp(val appName: String) : PhoneAction
    data class SetAlarm(val hour: Int, val minute: Int) : PhoneAction
    data class DialNumber(val number: String) : PhoneAction
    data class ComposeSms(val number: String, val message: String) : PhoneAction
}

data class ActionExecutionResult(val launched: Boolean, val message: String)

object PhoneActionPlanner {
    private val openAppPattern = Regex(
        """^(?:ouvre|lance|démarre|demarre)\s+(?:l'application\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val alarmPattern = Regex(
        """^(?:mets|programme|règle|regle|crée|cree)\s+(?:un\s+)?(?:réveil|reveil)\s+(?:à|pour)\s+(\d{1,2})(?:[:h](\d{2}))?h?$""",
        RegexOption.IGNORE_CASE
    )
    private val dialPattern = Regex(
        """^(?:appelle|compose le numéro)\s+([+0-9][0-9\s().-]{5,})$""",
        RegexOption.IGNORE_CASE
    )
    private val smsPattern = Regex(
        """^(?:envoie|écris|ecris)\s+(?:un\s+)?(?:sms|message)\s+à\s+([+0-9][0-9\s().-]{5,})\s*[:,-]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): PhoneAction? {
        val text = input.trim().trimEnd('.', '!', '?')
        if (text.isEmpty()) return null

        alarmPattern.matchEntire(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) return null
            return PhoneAction.SetAlarm(hour, minute)
        }

        smsPattern.matchEntire(text)?.let { match ->
            val number = normalizeNumber(match.groupValues[1]) ?: return null
            val message = match.groupValues[2].trim()
            if (message.isNotEmpty()) return PhoneAction.ComposeSms(number, message)
        }

        dialPattern.matchEntire(text)?.let { match ->
            normalizeNumber(match.groupValues[1])?.let { return PhoneAction.DialNumber(it) }
        }

        openAppPattern.matchEntire(text)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd('.', '!', '?')
            if (name.length in 2..60) return PhoneAction.OpenApp(name)
        }
        return null
    }

    private fun normalizeNumber(raw: String): String? {
        val number = raw.replace(Regex("[^0-9+]"), "")
        return number.takeIf { Regex("^\\+?[0-9]{6,15}$").matches(it) }
    }

    fun confirmationTitle(action: PhoneAction): String = when (action) {
        is PhoneAction.OpenApp -> "Ouvrir ${action.appName} ?"
        is PhoneAction.SetAlarm -> "Préparer un réveil ?"
        is PhoneAction.DialNumber -> "Ouvrir le composeur ?"
        is PhoneAction.ComposeSms -> "Préparer un SMS ?"
    }

    fun confirmationDescription(action: PhoneAction): String = when (action) {
        is PhoneAction.OpenApp ->
            "BOZER va chercher l'application « ${action.appName} » et l'ouvrir. Aucune autre action ne sera effectuée."
        is PhoneAction.SetAlarm ->
            "BOZER va ouvrir l'application Horloge avec l'heure ${"%02d:%02d".format(action.hour, action.minute)}. Tu devras confirmer le réveil dans Horloge."
        is PhoneAction.DialNumber ->
            "BOZER va ouvrir le composeur avec le numéro ${action.number}. Aucun appel ne sera passé automatiquement."
        is PhoneAction.ComposeSms ->
            "BOZER va ouvrir l'application SMS avec le numéro et le texte prérempli. Le message ne sera pas envoyé automatiquement."
    }

    /** Compatibility delegates; execution itself is owned by PhoneActionExecutor. */
    fun createIntent(context: Context, action: PhoneAction): Intent? =
        PhoneActionExecutor.createIntent(context, action)

    fun execute(
        context: Context,
        action: PhoneAction,
        confirmed: Boolean = false
    ): ActionExecutionResult = PhoneActionExecutor.execute(context, action, confirmed)

}
