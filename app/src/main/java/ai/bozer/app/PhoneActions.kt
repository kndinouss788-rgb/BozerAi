package ai.bozer.app

import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface PhoneAction {
    data class OpenApp(val appName: String) : PhoneAction
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val triggerAtMillis: Long = AlarmTimeParser.nextOccurrence(hour, minute)
    ) : PhoneAction
    data class DialNumber(val number: String) : PhoneAction
    data class ComposeSms(val number: String, val message: String) : PhoneAction
}

data class ActionExecutionResult(
    val launched: Boolean,
    val message: String,
    val retryable: Boolean = false
)

/** Resolves natural-language alarm times in the phone's current time zone. */
object AlarmTimeParser {
    private val localZone: ZoneId get() = ZoneId.systemDefault()

    fun resolve(day: String?, hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        require(hour in 0..23 && minute in 0..59) { "Heure de réveil invalide." }
        val normalizedDay = day.orEmpty().lowercase(Locale.ROOT)
        var resolvedHour = hour
        if (normalizedDay == "ce soir" && resolvedHour in 1..11) resolvedHour += 12
        val targetTime = LocalTime.of(resolvedHour, minute)
        val date = when (normalizedDay) {
            "demain" -> now.toLocalDate().plusDays(1)
            "aujourd'hui", "aujourd hui", "ce soir", "ce matin" -> now.toLocalDate()
            else -> if (targetTime.isAfter(now.toLocalTime())) now.toLocalDate()
                else now.toLocalDate().plusDays(1)
        }
        val target = LocalDateTime.of(date, targetTime)
        require(target.isAfter(now)) { "L'heure demandée est déjà passée." }
        return target.atZone(localZone).toInstant().toEpochMilli()
    }

    fun nextOccurrence(hour: Int, minute: Int): Long =
        resolve(null, hour, minute, LocalDateTime.now())
}

object PhoneActionPlanner {
    private val openAppPattern = Regex(
        """^(?:ouvre|lance|démarre|demarre)\s+(?:l'application\s+)?(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val alarmPattern = Regex(
        """^(?:réveille(?:-moi)?|reveille(?:-moi)?|(?:mets|programme|règle|regle|crée|cree)\s+(?:moi\s+)?(?:un\s+)?(?:réveil|reveil))(?:\s+(demain|aujourd'hui|aujourd hui|ce soir|ce matin))?\s+(?:à|a|pour)\s*(\d{1,2})(?:\s*(?:h|:)\s*(\d{1,2}))?$""",
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
            val day = match.groupValues[1].takeIf(String::isNotBlank)
            val hour = match.groupValues[2].toIntOrNull() ?: return null
            val minute = match.groupValues[3].takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) return null
            val triggerAt = try {
                AlarmTimeParser.resolve(day, hour, minute)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return PhoneAction.SetAlarm(hour, minute, triggerAt)
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
        is PhoneAction.SetAlarm -> "Programmer un réveil BOZER ?"
        is PhoneAction.DialNumber -> "Ouvrir le composeur ?"
        is PhoneAction.ComposeSms -> "Préparer un SMS ?"
    }

    fun confirmationDescription(action: PhoneAction): String = when (action) {
        is PhoneAction.OpenApp ->
            "BOZER va chercher l'application « ${action.appName} » et l'ouvrir. Aucune autre action ne sera effectuée."
        is PhoneAction.SetAlarm -> {
            val local = java.time.Instant.ofEpochMilli(action.triggerAtMillis)
                .atZone(ZoneId.systemDefault()).toLocalDateTime()
            "BOZER va programmer un réveil le " +
                local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)) +
                " sur ce téléphone. Tu peux annuler avant confirmation."
        }
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
