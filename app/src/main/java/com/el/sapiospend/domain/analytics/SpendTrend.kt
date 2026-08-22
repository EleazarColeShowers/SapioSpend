package com.el.sapiospend.domain.analytics

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One calendar month's spend. [total] is 0.0 for a month with no expenses at all. */
data class MonthlySpend(
    val monthStart: Long,
    val label: String,
    val total: Double
)

/**
 * Spend bucketed by calendar month — the series behind the trend chart.
 *
 * Pure JVM like [BudgetAnalytics], for the same reason: this is arithmetic about money
 * and it should be provable without an emulator.
 */
object SpendTrend {

    const val DEFAULT_MONTHS = 6

    /**
     * The last [months] calendar months, oldest first, **including months with no spend**.
     * A month with nothing in it is data — dropping it would slide the remaining bars
     * together and draw a steady trend over a gap where nothing was spent at all.
     *
     * The window normally ends on the current month. When that window would be entirely
     * empty but there is older spend, it slides back to end on the newest month that has
     * any, because a chart of six empty months tells a returning user nothing while the
     * history they actually have tells them plenty.
     */
    fun monthly(
        expenses: List<ExpenseEntity>,
        months: Int = DEFAULT_MONTHS,
        now: Long = System.currentTimeMillis()
    ): List<MonthlySpend> {
        val span = months.coerceAtLeast(1)
        val byMonth = expenses.groupBy { DateUtils.monthStart(it.dateCreated) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val currentMonth = DateUtils.monthStart(now)
        val windowStart = DateUtils.addMonths(currentMonth, -(span - 1))
        val lastMonthWithSpend = byMonth.keys.maxOrNull()
        val anchor = if (lastMonthWithSpend != null && lastMonthWithSpend < windowStart) {
            lastMonthWithSpend
        } else {
            currentMonth
        }

        return (span - 1 downTo 0).map { offset ->
            val monthStart = DateUtils.addMonths(anchor, -offset)
            MonthlySpend(
                monthStart = monthStart,
                label = monthLabel(monthStart),
                total = byMonth[monthStart] ?: 0.0
            )
        }
    }

    private fun monthLabel(monthStart: Long): String =
        SimpleDateFormat("MMM", Locale.getDefault()).format(Date(monthStart))
}
