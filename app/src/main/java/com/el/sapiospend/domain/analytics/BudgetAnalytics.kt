package com.el.sapiospend.domain.analytics

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import kotlin.math.max

/** One category of one event: what was planned, what was actually spent, and the gap. */
data class CategoryBreakdown(
    val category: String,
    val planned: Double,
    val actual: Double
) {
    /** Positive means overspent. */
    val variance: Double get() = actual - planned
    val isOverPlan: Boolean get() = planned > 0 && actual > planned
    /** True for spend against a category the plan never anticipated. */
    val isUnplanned: Boolean get() = planned == 0.0 && actual > 0
    val percentOfPlanUsed: Float
        get() = if (planned > 0) (actual / planned).toFloat() else 0f
}

data class EventAnalytics(
    val eventId: String,
    val eventName: String,
    val eventType: String,
    val budget: Double,
    val totalPlanned: Double,
    val totalSpent: Double,
    val expenseCount: Int,
    val categories: List<CategoryBreakdown>,
    /** Days the budget has been running, counting today. Never below 1. */
    val daysTracked: Int,
    val dailyBurnRate: Double,
    val periodStart: Long? = null,
    val periodEnd: Long? = null,
    /** Total days the period covers, or null when the budget is open-ended. */
    val periodLengthDays: Int? = null,
    /** Whole days left after today. 0 on the last day and after the period closes. */
    val daysRemaining: Int? = null
) {
    val remaining: Double get() = budget - totalSpent
    val isOverBudget: Boolean get() = totalSpent > budget
    val percentUsed: Float
        get() = if (budget > 0) (totalSpent / budget).toFloat() else 0f
    /** Budget the plan never assigned to a category — genuinely uncommitted money. */
    val unallocated: Double get() = budget - totalPlanned
    val overspentCategories: List<CategoryBreakdown> get() = categories.filter { it.isOverPlan }
    val biggestOverrun: CategoryBreakdown? get() = categories.maxByOrNull { it.variance }?.takeIf { it.variance > 0 }

    /** Whether this budget is bounded in time, and so has pacing figures at all. */
    val hasPeriod: Boolean get() = periodLengthDays != null

    val isPeriodOver: Boolean get() = daysRemaining == 0 && periodLengthDays?.let { daysTracked >= it } == true

    /** How far through the period we are, 0f..1f. */
    val percentOfPeriodElapsed: Float?
        get() = periodLengthDays?.let { (daysTracked.toFloat() / it).coerceIn(0f, 1f) }

    /**
     * What is left, spread over today and every remaining day — the single number a
     * salary earner actually acts on. Negative once the budget is blown, which the UI
     * shows rather than hides: pretending there is still a daily allowance left is worse
     * than saying the money is gone.
     */
    val safeDailySpend: Double?
        get() = daysRemaining?.let { remaining / (it + 1) }

    /** Where today's pace lands by the end of the period. */
    val projectedTotalSpend: Double?
        get() = periodLengthDays?.let { dailyBurnRate * it }

    /** Projected overshoot, or null when the pace lands inside the budget. */
    val projectedOverspend: Double?
        get() = projectedTotalSpend?.minus(budget)?.takeIf { it > 0 }

    /**
     * Spending faster than time is passing. Compared as fractions so it holds for any
     * budget size, and false for an open-ended budget where there is no pace to beat.
     */
    val isSpendingAheadOfPace: Boolean
        get() = percentOfPeriodElapsed?.let { percentUsed > it } == true
}

data class PortfolioAnalytics(
    val events: List<EventAnalytics>,
    val topCategories: List<CategoryBreakdown>
) {
    val totalBudget: Double get() = events.sumOf { it.budget }
    val totalSpent: Double get() = events.sumOf { it.totalSpent }
    val totalRemaining: Double get() = totalBudget - totalSpent
    val eventCount: Int get() = events.size
    val overBudgetCount: Int get() = events.count { it.isOverBudget }
    val percentUsed: Float
        get() = if (totalBudget > 0) (totalSpent / totalBudget).toFloat() else 0f
}

/**
 * All analytics maths in one place, with no Android or Room dependencies, so it runs as
 * a plain JVM unit test and can move to a server unchanged when reporting goes online.
 */
object BudgetAnalytics {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun forEvent(
        event: EventEntity,
        expenses: List<ExpenseEntity>,
        budgetLines: List<BudgetLineEntity>,
        now: Long = System.currentTimeMillis()
    ): EventAnalytics {
        val eventExpenses = expenses.filter { it.eventId == event.id }
        val eventLines = budgetLines.filter { it.eventId == event.id }

        val actualByCategory = eventExpenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val plannedByCategory = eventLines
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.plannedAmount } }

        // Union of both sides: a planned category with no spend is as interesting as
        // spend against a category nobody planned for.
        val categories = (plannedByCategory.keys + actualByCategory.keys)
            .map { category ->
                CategoryBreakdown(
                    category = category,
                    planned = plannedByCategory[category] ?: 0.0,
                    actual = actualByCategory[category] ?: 0.0
                )
            }
            .sortedByDescending { max(it.actual, it.planned) }

        val totalSpent = eventExpenses.sumOf { it.amount }

        // An event with no start date has been running since it was created; that is what
        // the app did before periods existed and what open-ended events still do.
        val start = event.startDate ?: event.dateCreated
        val periodLengthDays = event.endDate?.let { daysInclusive(start, it).coerceAtLeast(1) }
        val elapsed = daysInclusive(start, now)

        // Elapsed days stop accruing once the period closes, so a finished budget keeps
        // reporting the burn rate it actually ran at instead of decaying towards zero as
        // the months roll by. At least one day, so an event created minutes ago — or one
        // starting next week — cannot divide by zero.
        val daysTracked = (periodLengthDays?.let { elapsed.coerceAtMost(it) } ?: elapsed).coerceAtLeast(1)
        val daysRemaining = periodLengthDays?.let { (it - elapsed).coerceAtLeast(0) }

        return EventAnalytics(
            eventId = event.id,
            eventName = event.name,
            eventType = event.eventType,
            budget = event.budget,
            totalPlanned = eventLines.sumOf { it.plannedAmount },
            totalSpent = totalSpent,
            expenseCount = eventExpenses.size,
            categories = categories,
            daysTracked = daysTracked,
            dailyBurnRate = totalSpent / daysTracked,
            periodStart = event.startDate,
            periodEnd = event.endDate,
            periodLengthDays = periodLengthDays,
            daysRemaining = daysRemaining
        )
    }

    /** Whole days from [from] to [to], counting both ends. Negative spans collapse to 0. */
    private fun daysInclusive(from: Long, to: Long): Int =
        (((to - from) / MILLIS_PER_DAY) + 1).toInt().coerceAtLeast(0)

    fun portfolio(
        events: List<EventEntity>,
        expenses: List<ExpenseEntity>,
        budgetLines: List<BudgetLineEntity>,
        now: Long = System.currentTimeMillis()
    ): PortfolioAnalytics {
        val perEvent = events.map { forEvent(it, expenses, budgetLines, now) }

        val topCategories = perEvent
            .flatMap { it.categories }
            .groupBy { it.category }
            .map { (category, breakdowns) ->
                CategoryBreakdown(
                    category = category,
                    planned = breakdowns.sumOf { it.planned },
                    actual = breakdowns.sumOf { it.actual }
                )
            }
            .filter { it.actual > 0 }
            .sortedByDescending { it.actual }

        return PortfolioAnalytics(events = perEvent, topCategories = topCategories)
    }
}
