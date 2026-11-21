package ai.bozer.app

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

data class ParsedSecretaryTaskDetails(
    val title: String,
    val dueAtMillis: Long?,
    val dateWarning: String? = null
)

/**
 * Small deterministic French date/time parser for Secretary voice commands.
 * It deliberately supports a narrow, explicit set of trailing date forms
 * instead of guessing from arbitrary prose.
 */
object SecretaryDateTimeParser {
    private data class TimePart(val hour: Int, val minute: Int)
    private sealed interface DatePart {
        data class RelativeDays(val days: Int) : DatePart
        data class Weekday(val calendarDay: Int) : DatePart
        data class MonthDay(val day: Int, val month: Int, val year: Int?) : DatePart
    }

    fun parse(titleInput: String, nowMillis: Long = System.currentTimeMillis()): ParsedSecretaryTaskDetails {
        var title = titleInput.trim().trimEnd('.', '!', '?')
        if (title.isBlank()) return ParsedSecretaryTaskDetails(title, null)

        val timeMatch = TIME_AT_END.find(normalize(title))
        val time = timeMatch?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = (match.groupValues[2].ifBlank { match.groupValues[3] }).toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && minute in 0..59) {
                title = title.substring(0, match.range.first).trim()
                TimePart(hour, minute)
            } else null
        }

        val durationMatch = DURATION_AT_END.find(normalize(title))
        if (durationMatch != null) {
            val amount = durationMatch.groupValues[1].toLongOrNull()
            val unit = durationMatch.groupValues[2]
            if (amount != null && amount in 1..10_000) {
                val multiplier = when {
                    unit.startsWith("min") -> 60_000L
                    unit.startsWith("h") -> 3_600_000L
                    else -> 86_400_000L
                }
                val due = nowMillis + amount * multiplier
                title = title.substring(0, durationMatch.range.first).trim()
                return ParsedSecretaryTaskDetails(title, due.takeIf { it > nowMillis })
            }
        }

        val normalizedTitle = normalize(title)
        val dateMatch = DATE_AT_END.find(normalizedTitle)
        val datePart = dateMatch?.let(::toDatePart)
        if (datePart != null) {
            title = title.substring(0, dateMatch.range.first).trim()
            val dueAt = buildDate(
                datePart,
                time ?: if (datePart == DatePart.RelativeDays(0)) TimePart(23, 59) else TimePart(9, 0),
                nowMillis
            )
            return if (dueAt == null) {
                ParsedSecretaryTaskDetails(
                    titleInput.trim(),
                    null,
                    "La date ou l'heure indiquée est invalide ou déjà passée. Vérifie l'échéance et réessaie."
                )
            } else {
                ParsedSecretaryTaskDetails(title, dueAt)
            }
        }

        if (time != null) {
            val today = calendarAt(nowMillis, time)
            if (today.timeInMillis > nowMillis) {
                title = title.trim()
                return ParsedSecretaryTaskDetails(title, today.timeInMillis)
            }
            today.add(Calendar.DAY_OF_YEAR, 1)
            return ParsedSecretaryTaskDetails(title, today.timeInMillis)
        }

        return ParsedSecretaryTaskDetails(title, null)
    }

    private fun toDatePart(match: MatchResult): DatePart? {
        val value = match.value.trim()
        return when {
            value == "aujourd'hui" || value == "aujourd hui" -> DatePart.RelativeDays(0)
            value == "demain" -> DatePart.RelativeDays(1)
            value == "apres demain" || value == "apres-demain" -> DatePart.RelativeDays(2)
            value == "ce soir" -> DatePart.RelativeDays(0)
            else -> {
                val weekdayMatch = WEEKDAY_AT_END.find(value)
                if (weekdayMatch != null) {
                    val day = when (weekdayMatch.groupValues[1]) {
                        "lundi" -> Calendar.MONDAY
                        "mardi" -> Calendar.TUESDAY
                        "mercredi" -> Calendar.WEDNESDAY
                        "jeudi" -> Calendar.THURSDAY
                        "vendredi" -> Calendar.FRIDAY
                        "samedi" -> Calendar.SATURDAY
                        "dimanche" -> Calendar.SUNDAY
                        else -> return null
                    }
                    return DatePart.Weekday(day)
                }

                val numeric = NUMERIC_DATE_AT_END.find(value)
                if (numeric != null) {
                    val day = numeric.groupValues[1].toIntOrNull() ?: return null
                    val month = numeric.groupValues[2].toIntOrNull()?.minus(1) ?: return null
                    val year = numeric.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
                    if (day !in 1..31 || month !in 0..11 || (year != null && year !in 2020..2100)) return null
                    return DatePart.MonthDay(day, month, year)
                }

                val named = MONTH_DATE_AT_END.find(value) ?: return null
                val day = named.groupValues[1].toIntOrNull() ?: return null
                val month = MONTHS.indexOf(named.groupValues[2])
                if (day !in 1..31 || month !in 0..11) return null
                DatePart.MonthDay(day, month, null)
            }
        }
    }

    private fun buildDate(part: DatePart, time: TimePart, nowMillis: Long): Long? {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val candidate = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (part) {
            is DatePart.RelativeDays -> candidate.add(Calendar.DAY_OF_YEAR, part.days)
            is DatePart.Weekday -> {
                var added = 0
                while (candidate.get(Calendar.DAY_OF_WEEK) != part.calendarDay && added < 7) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                    added++
                }
                if (candidate.timeInMillis <= nowMillis) candidate.add(Calendar.DAY_OF_YEAR, 7)
            }
            is DatePart.MonthDay -> {
                candidate.set(Calendar.DAY_OF_MONTH, 1)
                candidate.set(Calendar.MONTH, part.month)
                candidate.set(Calendar.DAY_OF_MONTH, part.day)
                val year = part.year ?: now.get(Calendar.YEAR)
                candidate.set(Calendar.YEAR, year)
                if (!isSameDate(candidate, part.day, part.month, year)) return null
                if (candidate.timeInMillis <= nowMillis && part.year == null) {
                    candidate.add(Calendar.YEAR, 1)
                }
                if (candidate.timeInMillis <= nowMillis) return null
            }
        }
        return candidate.timeInMillis.takeIf { it > nowMillis }
    }

    private fun calendarAt(nowMillis: Long, time: TimePart): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun isSameDate(calendar: Calendar, day: Int, month: Int, year: Int): Boolean =
        calendar.get(Calendar.DAY_OF_MONTH) == day &&
            calendar.get(Calendar.MONTH) == month &&
            calendar.get(Calendar.YEAR) == year

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val TIME_AT_END = Regex("""\s+(?:a|à)\s*(\d{1,2})(?:\s*h\s*(\d{2})?|\s*:\s*(\d{2}))?\s*$""")
    private val DURATION_AT_END = Regex("""\s+dans\s+(\d{1,5})\s+(minutes?|mins?|heures?|hrs?|h|jours?)$""")
    private val DATE_AT_END = Regex(
        """\s+(aujourd'hui|aujourd hui|demain|apres[- ]demain|ce soir|(?:le\s+)?(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)|(?:le\s+)?\d{1,2}[/-]\d{1,2}(?:[/-]\d{4})?|(?:le\s+)?\d{1,2}(?:er)?\s+(?:janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre))$"""
    )
    private val WEEKDAY_AT_END = Regex("""(?:le\s+)?(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)$""")
    private val NUMERIC_DATE_AT_END = Regex("""(?:le\s+)?(\d{1,2})[/-](\d{1,2})(?:[/-](\d{4}))?$""")
    private val MONTH_DATE_AT_END = Regex(
        """(?:le\s+)?(\d{1,2})(?:er)?\s+(janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre)$"""
    )
    private val MONTHS = listOf(
        "janvier", "fevrier", "mars", "avril", "mai", "juin",
        "juillet", "aout", "septembre", "octobre", "novembre", "decembre"
    )
}
