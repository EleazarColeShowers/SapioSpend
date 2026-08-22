package com.el.sapiospend

import com.el.sapiospend.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The calendar maths behind budget periods and the month grid.
 *
 * Every expectation is built with Calendar rather than a hardcoded epoch millis, so the
 * tests hold in whatever timezone the machine running them happens to be in.
 */
class DateUtilsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 30, 15) }.timeInMillis

    private fun fieldOf(millis: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(field)

    @Test
    fun `monthBounds covers the first instant to the last of the month`() {
        val (start, end) = DateUtils.monthBounds(0, at(2026, Calendar.AUGUST, 15))

        assertEquals(1, fieldOf(start, Calendar.DAY_OF_MONTH))
        assertEquals(0, fieldOf(start, Calendar.HOUR_OF_DAY))
        assertEquals(0, fieldOf(start, Calendar.MINUTE))
        assertEquals(31, fieldOf(end, Calendar.DAY_OF_MONTH))
        assertEquals(23, fieldOf(end, Calendar.HOUR_OF_DAY))
        assertEquals(59, fieldOf(end, Calendar.MINUTE))
        assertEquals(Calendar.AUGUST, fieldOf(end, Calendar.MONTH))
    }

    @Test
    fun `monthBounds takes the last day from the calendar so leap years work`() {
        val leap = DateUtils.monthBounds(0, at(2024, Calendar.FEBRUARY, 10))
        val common = DateUtils.monthBounds(0, at(2026, Calendar.FEBRUARY, 10))

        assertEquals(29, fieldOf(leap.second, Calendar.DAY_OF_MONTH))
        assertEquals(28, fieldOf(common.second, Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `next month crosses the year boundary`() {
        val (start, end) = DateUtils.monthBounds(1, at(2026, Calendar.DECEMBER, 5))

        assertEquals(2027, fieldOf(start, Calendar.YEAR))
        assertEquals(Calendar.JANUARY, fieldOf(start, Calendar.MONTH))
        assertEquals(31, fieldOf(end, Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `daysInPeriod counts both ends`() {
        val start = at(2026, Calendar.AUGUST, 1)
        assertEquals(31, DateUtils.daysInPeriod(start, at(2026, Calendar.AUGUST, 31)))
        // A single-day period is one day, not zero.
        assertEquals(1, DateUtils.daysInPeriod(start, start))
    }

    @Test
    fun `daysInPeriod ignores the time of day at either end`() {
        // Late on the first day to early on the last: still two whole days.
        val start = Calendar.getInstance().apply { clear(); set(2026, Calendar.AUGUST, 1, 23, 45) }.timeInMillis
        val end = Calendar.getInstance().apply { clear(); set(2026, Calendar.AUGUST, 2, 0, 5) }.timeInMillis

        assertEquals(2, DateUtils.daysInPeriod(start, end))
    }

    @Test
    fun `a month grid always fits inside six weeks`() {
        // Every month of a leap year, since February starting on a Sunday is the tightest
        // case and a 31-day month starting on a Saturday is the widest.
        (0..11).forEach { month ->
            val monthStart = DateUtils.monthStart(at(2024, month, 15))
            val cells = DateUtils.leadingBlanks(monthStart) + DateUtils.daysInMonth(monthStart)

            assertTrue("month $month has ${DateUtils.leadingBlanks(monthStart)} blanks", DateUtils.leadingBlanks(monthStart) in 0..6)
            assertTrue("month $month needs $cells cells", cells <= 42)
        }
    }

    @Test
    fun `dayOfMonth returns the start of the requested day`() {
        val monthStart = DateUtils.monthStart(at(2026, Calendar.AUGUST, 15))
        val fifth = DateUtils.dayOfMonth(monthStart, 5)

        assertEquals(5, fieldOf(fifth, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, fieldOf(fifth, Calendar.MONTH))
        assertEquals(0, fieldOf(fifth, Calendar.HOUR_OF_DAY))
        assertEquals(0, fieldOf(fifth, Calendar.MILLISECOND))
    }

    @Test
    fun `addMonths steps whole months without drifting off the first`() {
        val january = DateUtils.monthStart(at(2026, Calendar.JANUARY, 31))

        val february = DateUtils.addMonths(january, 1)
        assertEquals(Calendar.FEBRUARY, fieldOf(february, Calendar.MONTH))
        assertEquals(1, fieldOf(february, Calendar.DAY_OF_MONTH))

        val lastDecember = DateUtils.addMonths(january, -1)
        assertEquals(Calendar.DECEMBER, fieldOf(lastDecember, Calendar.MONTH))
        assertEquals(2025, fieldOf(lastDecember, Calendar.YEAR))
    }

    @Test
    fun `weekday headers are seven single letters`() {
        val initials = DateUtils.weekdayInitials()

        assertEquals(7, initials.size)
        assertTrue(initials.all { it.length == 1 })
    }

    @Test
    fun `the weekday header lines up with the leading blanks`() {
        // The blank count is measured from the same first-day-of-week the header is built
        // from; if the two ever disagree the whole grid shifts by a column.
        val monthStart = DateUtils.monthStart(at(2026, Calendar.AUGUST, 15))
        val firstDayColumn = DateUtils.leadingBlanks(monthStart)

        val calendar = Calendar.getInstance().apply { timeInMillis = monthStart }
        val expectedColumn = ((calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek) + 7) % 7

        assertEquals(expectedColumn, firstDayColumn)
    }

    @Test
    fun `picking today keeps the current clock time`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 22, 14, 37, 5)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(now, DateUtils.instantOnDay(DateUtils.startOfDay(now), now))
    }

    @Test
    fun `picking another day stores an instant inside that day`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 22, 14, 37, 5)
        }.timeInMillis
        val yesterday = DateUtils.startOfDay(now) - 1

        val stored = DateUtils.instantOnDay(yesterday, now)

        // Inside the day at both ends, so a period covering that day contains it.
        assertTrue(stored >= DateUtils.startOfDay(yesterday))
        assertTrue(stored <= DateUtils.endOfDay(yesterday))
    }
}
