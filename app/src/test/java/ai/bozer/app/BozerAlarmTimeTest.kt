package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BozerAlarmTimeTest {
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
    fun recurringAlarmMovesToTheNextSelectedWeekday() {
        val previous = localTime(2026, Calendar.NOVEMBER, 21, 7, 30)
        val now = localTime(2026, Calendar.NOVEMBER, 21, 8, 0)
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowMask = 1 shl (tomorrow.get(Calendar.DAY_OF_WEEK) - 1)

        val next = BozerAlarmTime.nextOccurrence(previous, now, tomorrowMask)

        assertNotNull(next)
        val actualMillis = requireNotNull(next)
        val actual = Calendar.getInstance().apply { timeInMillis = actualMillis }
        assertEquals(tomorrow.get(Calendar.YEAR), actual.get(Calendar.YEAR))
        assertEquals(tomorrow.get(Calendar.DAY_OF_YEAR), actual.get(Calendar.DAY_OF_YEAR))
        assertEquals(7, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, actual.get(Calendar.MINUTE))
        assertTrue(actualMillis > now)
    }

    @Test
    fun missedAlarmRepeatingOnlyTodayMovesToNextWeek() {
        val previous = localTime(2026, Calendar.NOVEMBER, 21, 7, 30)
        val now = localTime(2026, Calendar.NOVEMBER, 21, 8, 0)
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val todayMask = 1 shl (today.get(Calendar.DAY_OF_WEEK) - 1)

        val next = BozerAlarmTime.nextOccurrence(previous, now, todayMask)

        assertNotNull(next)
        val actualMillis = requireNotNull(next)
        val actual = Calendar.getInstance().apply { timeInMillis = actualMillis }
        assertEquals(today.get(Calendar.DAY_OF_WEEK), actual.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, actual.get(Calendar.MINUTE))
        assertTrue(actualMillis > now)
    }

    @Test
    fun invalidRepeatMasksReturnNull() {
        val previous = localTime(2026, Calendar.NOVEMBER, 21, 7, 30)
        assertNull(BozerAlarmTime.nextOccurrence(previous, previous, 0))
        assertNull(BozerAlarmTime.nextOccurrence(previous, previous, 128))
    }
}
