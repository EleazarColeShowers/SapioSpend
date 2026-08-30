package com.el.sapiospend

import com.el.sapiospend.domain.notify.TickSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TickScheduleTest {

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun dayOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    @Test
    fun `later today when the hour has not passed`() {
        val next = TickSchedule.nextTick(hourOfDay = 9, now = at(7, 30))

        assertEquals(9, hourOf(next))
        assertEquals(10, dayOf(next))
    }

    @Test
    fun `tomorrow when the hour has already gone`() {
        val next = TickSchedule.nextTick(hourOfDay = 9, now = at(14))

        assertEquals(9, hourOf(next))
        assertEquals(11, dayOf(next))
    }

    /**
     * The tick reschedules itself the moment it fires, so an implementation that accepted
     * "now" as the next occurrence would fire again immediately and spin.
     */
    @Test
    fun `firing exactly on the hour schedules the following day`() {
        val fired = at(9)

        val next = TickSchedule.nextTick(hourOfDay = 9, now = fired)

        assertTrue(next > fired)
        assertEquals(11, dayOf(next))
    }
}
