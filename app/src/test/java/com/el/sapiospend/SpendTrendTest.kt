package com.el.sapiospend

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.SpendTrend
import com.el.sapiospend.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SpendTrendTest {

    /** Midday on the 15th, so no assertion here is sitting on a month boundary. */
    private fun instant(year: Int, month: Int, day: Int = 15): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun expense(amount: Double, at: Long) =
        ExpenseEntity(eventId = "e1", title = "t", category = "Food", amount = amount, dateCreated = at)

    @Test
    fun `the window is the requested number of months, ending on the current one`() {
        val now = instant(2026, Calendar.AUGUST)

        val series = SpendTrend.monthly(emptyList(), months = 6, now = now)

        assertEquals(6, series.size)
        assertEquals(DateUtils.monthStart(now), series.last().monthStart)
        assertEquals(DateUtils.monthStart(instant(2026, Calendar.MARCH)), series.first().monthStart)
    }

    @Test
    fun `expenses land in the calendar month they were logged in`() {
        val now = instant(2026, Calendar.AUGUST)
        val expenses = listOf(
            expense(1000.0, instant(2026, Calendar.AUGUST, 2)),
            expense(500.0, instant(2026, Calendar.AUGUST, 28)),
            expense(300.0, instant(2026, Calendar.JULY))
        )

        val series = SpendTrend.monthly(expenses, months = 3, now = now)

        assertEquals(listOf(0.0, 300.0, 1500.0), series.map { it.total })
    }

    @Test
    fun `a month with no spend is kept in the series at zero`() {
        val now = instant(2026, Calendar.AUGUST)
        val expenses = listOf(
            expense(1000.0, instant(2026, Calendar.AUGUST)),
            expense(400.0, instant(2026, Calendar.JUNE))
        )

        val series = SpendTrend.monthly(expenses, months = 3, now = now)

        // June, July, August — July stays as a zero rather than collapsing so that June
        // and August sit next to each other.
        assertEquals(listOf(400.0, 0.0, 1000.0), series.map { it.total })
    }

    @Test
    fun `the window slides back when nothing was spent inside it`() {
        val now = instant(2026, Calendar.AUGUST)
        val expenses = listOf(expense(2500.0, instant(2025, Calendar.NOVEMBER)))

        val series = SpendTrend.monthly(expenses, months = 3, now = now)

        assertEquals(DateUtils.monthStart(instant(2025, Calendar.NOVEMBER)), series.last().monthStart)
        assertEquals(2500.0, series.last().total, 0.0)
    }

    @Test
    fun `the window stays on the current month while there is spend inside it`() {
        val now = instant(2026, Calendar.AUGUST)
        val expenses = listOf(
            expense(2500.0, instant(2020, Calendar.JANUARY)),
            expense(100.0, instant(2026, Calendar.JULY))
        )

        val series = SpendTrend.monthly(expenses, months = 3, now = now)

        assertEquals(DateUtils.monthStart(now), series.last().monthStart)
        assertEquals(0.0, series.last().total, 0.0)
    }

    @Test
    fun `an empty history still produces a readable series`() {
        val series = SpendTrend.monthly(emptyList(), months = 6, now = instant(2026, Calendar.AUGUST))

        assertEquals(6, series.size)
        assertEquals(0.0, series.sumOf { it.total }, 0.0)
    }
}
