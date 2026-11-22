package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SecretaryDateTimeParserTest {
    private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun parsesTomorrowWithExplicitTime() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Appeler maman demain à 18h30", now)

        assertEquals("Appeler maman", parsed.title)
        val due = requireNotNull(parsed.dueAtMillis)
        val actual = Calendar.getInstance().apply { timeInMillis = due }
        assertEquals(22, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, actual.get(Calendar.MINUTE))
        assertTrue(due > now)
    }

    @Test
    fun parsesNextWeekdayAndDefaultsToNine() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Envoyer le devis lundi", now)

        assertEquals("Envoyer le devis", parsed.title)
        val actual = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed.dueAtMillis) }
        assertEquals(Calendar.MONDAY, actual.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, actual.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun sameDayWithoutTimeDefaultsToEndOfDay() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Lire le contrat aujourd'hui", now)

        assertEquals("Lire le contrat", parsed.title)
        val actual = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed.dueAtMillis) }
        assertEquals(21, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, actual.get(Calendar.MINUTE))
    }

    @Test
    fun parsesFrenchMonthDateAndTime() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Rappeler le fournisseur le 25 novembre à 17h", now)

        assertEquals("Rappeler le fournisseur", parsed.title)
        val actual = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed.dueAtMillis) }
        assertEquals(Calendar.NOVEMBER, actual.get(Calendar.MONTH))
        assertEquals(25, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, actual.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun parsesRelativeDuration() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Appeler le garage dans 2 heures", now)

        assertEquals("Appeler le garage", parsed.title)
        assertEquals(now + 2 * 60 * 60 * 1000L, parsed.dueAtMillis)
    }

    @Test
    fun invalidTimeWithDateIsRejectedInsteadOfUsingDefaultTime() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Appeler maman demain à 28h", now)

        assertEquals("Appeler maman demain à 28h", parsed.title)
        assertNull(parsed.dueAtMillis)
        assertNotNull(parsed.dateWarning)
    }

    @Test
    fun invalidCalendarDateReturnsAWarning() {
        val now = localTime(2026, Calendar.JANUARY, 1, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Préparer le dossier le 31 février", now)

        assertEquals("Préparer le dossier", parsed.title)
        assertNull(parsed.dueAtMillis)
        assertNotNull(parsed.dateWarning)
    }

    @Test
    fun pastSameDayTimeReturnsAnExplicitWarning() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Appeler maman aujourd'hui à 8h", now)

        assertEquals("Appeler maman", parsed.title)
        assertNull(parsed.dueAtMillis)
        assertNotNull(parsed.dateWarning)
    }

    @Test
    fun excessiveRelativeDurationReturnsWarning() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Appeler le garage dans 10001 jours", now)

        assertEquals("Appeler le garage dans 10001 jours", parsed.title)
        assertNull(parsed.dueAtMillis)
        assertNotNull(parsed.dateWarning)
    }

    @Test
    fun invalidTimeIsNotGuessed() {
        val now = localTime(2026, Calendar.NOVEMBER, 21, 10, 0)
        val parsed = SecretaryDateTimeParser.parse("Préparer le dossier à 28h", now)

        assertEquals("Préparer le dossier à 28h", parsed.title)
        assertNull(parsed.dueAtMillis)
        assertNotNull(parsed.dateWarning)
    }
}
