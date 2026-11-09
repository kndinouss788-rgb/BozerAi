package ai.bozer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.net.Uri
import android.content.pm.PackageManager
import java.text.Normalizer
import java.util.Locale

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

    private fun launchIntent(context: Context, appName: String): Intent? {
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(
            launcherQuery,
            PackageManager.MATCH_ALL
        )
        val normalizedTarget = normalizeAppLabel(appName)
        if (normalizedTarget in setOf("parametres", "reglages", "settings", "configuration")) {
            return context.packageManager.getLaunchIntentForPackage("com.android.settings")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val match = matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                normalizeAppLabel(info.loadLabel(context.packageManager).toString()) == normalizedTarget
        } ?: matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                normalizeAppLabel(info.loadLabel(context.packageManager).toString())
                    .contains(normalizedTarget)
        } ?: return null

        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun normalizeAppLabel(value: String): String {
        var normalized = Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ").trim()
        listOf("l'application ", "l'appli ", "les ", "le ", "la ", "l'").forEach { prefix ->
            if (normalized.startsWith(prefix)) {
                normalized = normalized.removePrefix(prefix).trim()
            }
        }
        return normalized
    }

    fun createIntent(context: Context, action: PhoneAction): Intent? = when (action) {
        is PhoneAction.OpenApp -> launchIntent(context, action.appName)
        is PhoneAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        is PhoneAction.DialNumber -> Intent(
            Intent.ACTION_DIAL,
            Uri.fromParts("tel", action.number, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is PhoneAction.ComposeSms -> Intent(
            Intent.ACTION_SENDTO,
            Uri.fromParts("smsto", action.number, null)
        ).apply {
            putExtra("sms_body", action.message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun execute(context: Context, action: PhoneAction): ActionExecutionResult {
        val intent = createIntent(context, action)
            ?: return ActionExecutionResult(false, "Application introuvable. Rien n'a été ouvert.")
        if (intent.resolveActivity(context.packageManager) == null) {
            return ActionExecutionResult(false, "Aucune application compatible n'est installée. Rien n'a été lancé.")
        }
        return try {
            context.startActivity(intent)
            val message = when (action) {
                is PhoneAction.OpenApp -> "L'application ${action.appName} a été ouverte."
                is PhoneAction.SetAlarm -> "L'application Horloge a été ouverte pour préparer le réveil. Vérifie et confirme l'heure dans Horloge."
                is PhoneAction.DialNumber -> "Le composeur a été ouvert. Aucun appel n'a été passé."
                is PhoneAction.ComposeSms -> "L'application SMS a été ouverte avec le brouillon. Le message n'a pas été envoyé."
            }
            ActionExecutionResult(true, message)
        } catch (_: ActivityNotFoundException) {
            ActionExecutionResult(false, "Aucune application compatible n'a pu être ouverte. Rien n'a été exécuté.")
        } catch (error: SecurityException) {
            ActionExecutionResult(false, "Android a refusé cette action : ${error.message ?: "autorisation manquante"}.")
        }
    }
}
