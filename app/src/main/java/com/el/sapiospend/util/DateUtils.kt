package com.el.sapiospend.util

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar maths for budget periods.
 *
 * java.util.Calendar rather than java.time because minSdk is 24 and core library
 * desugaring is not enabled — java.time would compile and then crash on a real Android 7
 * device, which is still a meaningful share of the Nigerian market.
 *
 * Every boundary is a local-time instant: a period runs from 00:00:00.000 on its first
 * day to 23:59:59.999 on its last, so an expense logged at any point on the closing day
 * still falls inside it.
 */
object DateUtils {

    /** Start and end instants of the calendar month containing [now], shifted by [monthOffset]. */
    fun monthBounds(monthOffset: Int = 0, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = cal.atStartOfDay()
        // Last day taken from the calendar rather than a 30/31 table, so February and
        // leap years take care of themselves.
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        return start to cal.atEndOfDay()
    }

    fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply { timeInMillis = millis }.atStartOfDay()

    fun endOfDay(millis: Long): Long =
        Calendar.getInstance().apply { timeInMillis = millis }.atEndOfDay()

    // --- Month grid ---------------------------------------------------------------
    // What the calendar component needs to lay out a month. Everything here works in
    // local time, so a day the user taps is the day that gets stored.

    /** First instant of the month containing [millis]. The grid's anchor. */
    fun monthStart(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
        }.atStartOfDay()

    fun addMonths(monthStart: Long, delta: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(Calendar.MONTH, delta)
        }.atStartOfDay()

    fun daysInMonth(monthStart: Long): Int =
        Calendar.getInstance().apply { timeInMillis = monthStart }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Start of the nth day of the month [monthStart] belongs to. */
    fun dayOfMonth(monthStart: Long, day: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = monthStart
            set(Calendar.DAY_OF_MONTH, day)
        }.atStartOfDay()

    /**
     * Empty cells before the 1st. Measured against the locale's own first day of the
     * week, so the grid lines up with whatever the weekday header says.
     */
    fun leadingBlanks(monthStart: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        return ((cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek) + 7) % 7
    }

    /** Single-letter weekday headers, ordered from the locale's first day of the week. */
    fun weekdayInitials(): List<String> {
        val shortNames = DateFormatSymbols.getInstance().shortWeekdays
        val first = Calendar.getInstance().firstDayOfWeek
        return (0..6).map { offset ->
            // shortWeekdays is 1-indexed by Calendar.SUNDAY..SATURDAY, with a blank at 0.
            val dayOfWeek = ((first - 1 + offset) % 7) + 1
            shortNames[dayOfWeek].take(1).uppercase(Locale.getDefault())
        }
    }

    /** Whole days from [start] to [end] counting both ends — how a period is described. */
    fun daysInPeriod(start: Long, end: Long): Int =
        (((endOfDay(end) - startOfDay(start)) / (24L * 60 * 60 * 1000)) + 1).toInt().coerceAtLeast(1)

    /**
     * The instant to store for a day the user tapped on a calendar.
     *
     * Picking today keeps the current clock time, so an expense logged now still sorts
     * above the ones logged this morning. Any other day is stored at midday — inside the
     * day whichever way the clocks move, and far from the boundaries a period's
     * start-of-day and end-of-day instants are drawn at.
     */
    fun instantOnDay(day: Long, now: Long = System.currentTimeMillis()): Long =
        if (startOfDay(day) == startOfDay(now)) now else startOfDay(day) + 12 * 60 * 60 * 1000L

    fun formatMonthYear(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

    private fun Calendar.atStartOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }

    private fun Calendar.atEndOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
        return timeInMillis
    }
}

/** "Aug 1 – Aug 31, 2026" style label for a period, collapsing a shared year. */
fun formatPeriod(start: Long?, end: Long?): String? = when {
    start != null && end != null -> "${start.formatShortDate()} – ${end.formatDate()}"
    start != null -> "From ${start.formatDate()}"
    end != null -> "Until ${end.formatDate()}"
    else -> null
}

private fun Long.formatShortDate(): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
