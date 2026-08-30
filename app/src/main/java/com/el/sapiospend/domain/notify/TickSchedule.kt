package com.el.sapiospend.domain.notify

import java.util.Calendar

/**
 * When the next daily tick should land.
 *
 * Separate from the alarm code so the one thing that is easy to get wrong — rolling over
 * to tomorrow when the chosen hour has already passed today — is a unit test rather than
 * a bug you notice a day late.
 */
object TickSchedule {

    /**
     * The next occurrence of [hourOfDay] strictly after [now], in local time.
     *
     * Strictly after, so a tick that fires at 09:00:00.000 and immediately reschedules
     * cannot pick the same instant again and spin.
     */
    fun nextTick(hourOfDay: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
