package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAlarmCommandParserTest {
    @Test
    fun parsesBozerAlarmAndWakeUpPhrases() {
        assertEquals(AndroidCommand.SetAlarm(7, 30, "BOZER"), AndroidCommandParser.parse("mets une alarme à 7h30"))
        assertEquals(AndroidCommand.SetAlarm(6, 0, "BOZER"), AndroidCommandParser.parse("réveille-moi à 6h"))
        assertEquals(AndroidCommand.SetAlarm(18, 15, "prendre le dossier"), AndroidCommandParser.parse("rappelle-moi à 18h15 pour prendre le dossier"))
    }

    @Test
    fun parsesDailyAndWeekdayRecurringAlarms() {
        assertEquals(AndroidCommand.SetRecurringAlarm(6, 0, "BOZER", 127),
            AndroidCommandParser.parse("programme une alarme tous les jours à 6h"))
        assertEquals(AndroidCommand.SetRecurringAlarm(7, 30, "BOZER", 62),
            AndroidCommandParser.parse("mets une alarme du lundi au vendredi à 7h30"))
    }

    @Test
    fun recurringAlarmCalculatesNextAllowedWeekday() {
        val previous = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 9, 6, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 9, 7, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expected = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 6, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, BozerAlarmTime.nextOccurrence(previous, now, 62))
    }

    @Test
    fun parsesRelativeRemindersAndBoundsThem() {
        assertEquals(AndroidCommand.SetReminder(600, "appeler maman"), AndroidCommandParser.parse("rappelle-moi dans 10 minutes d'appeler maman"))
        assertEquals(AndroidCommand.SetReminder(7200, "Rappel BOZER"), AndroidCommandParser.parse("rappelle-moi dans 2 heures"))
        assertNull(AndroidCommandParser.parse("rappelle-moi dans 999999 minutes"))
    }

    @Test
    fun parsesAlarmManagementPhrasesThroughExecutorEntryPointOnly() {
        assertNull(AndroidCommandParser.parse("annule la prochaine alarme"))
        assertNull(AndroidCommandParser.parse("quelles sont mes alarmes"))
    }
}
