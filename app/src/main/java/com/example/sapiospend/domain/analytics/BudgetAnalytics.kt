package com.example.sapiospend.domain.analytics

import com.example.sapiospend.data.local.BudgetLineEntity
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.ExpenseEntity
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
    val daysTracked: Int,
    val dailyBurnRate: Double
) {
    val remaining: Double get() = budget - totalSpent
    val isOverBudget: Boolean get() = totalSpent > budget
    val percentUsed: Float
        get() = if (budget > 0) (totalSpent / budget).toFloat() else 0f
    /** Budget the plan never assigned to a category — genuinely uncommitted money. */
    val unallocated: Double get() = budget - totalPlanned
    val overspentCategories: List<CategoryBreakdown> get() = categories.filter { it.isOverPlan }
    val biggestOverrun: CategoryBreakdown? get() = categories.maxByOrNull { it.variance }?.takeIf { it.variance > 0 }
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
        // At least one day, so an event created minutes ago doesn't divide by zero and
        // report an absurd burn rate.
        val daysTracked = (((now - event.dateCreated) / MILLIS_PER_DAY) + 1).toInt().coerceAtLeast(1)

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
            dailyBurnRate = totalSpent / daysTracked
        )
    }

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
