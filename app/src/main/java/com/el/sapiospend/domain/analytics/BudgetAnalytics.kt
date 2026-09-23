package com.el.sapiospend.domain.analytics

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.domain.funding.Funding
import com.el.sapiospend.domain.funding.FundingSummary
import com.el.sapiospend.domain.payment.PaymentSummary
import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.FxRates
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
    val daysRemaining: Int? = null,
    /** Committed against paid for this event's expenses. */
    val payments: PaymentSummary = Payments.EMPTY,
    /** Money promised and received towards the event. */
    val funding: FundingSummary = Funding.EMPTY,
    /** Heads to divide by, or null when the event has no guest count. */
    val guestCount: Int? = null,
    /** Which way money moves through this budget. */
    val direction: BudgetDirection = BudgetDirection.DEFAULT,
    /**
     * The currency every figure in here is denominated in — this budget's own.
     *
     * Every number on this object comes from one budget, so they are all in the same
     * unit and the maths needs no conversion. It is carried so that a caller pooling
     * several of these knows what it is adding up; see [PortfolioAnalytics].
     */
    val currency: AppCurrency = AppCurrency.DEFAULT
) {
    /** True when [budget] is a target to reach rather than money to spend. */
    val isSavingsGoal: Boolean get() = direction.isSaving

    val remaining: Double get() = budget - totalSpent

    /** What has actually left the account, as against what has been committed. */
    val totalPaid: Double get() = payments.paid

    /** Bills booked and not yet settled — the money still to go out. */
    val outstanding: Double get() = payments.outstanding

    /**
     * Cash received less cash paid out.
     *
     * The figure that decides whether the next vendor can be paid this week, which
     * neither the budget nor the spend total can answer on its own: an event can be
     * comfortably under budget and still have nothing in the account.
     */
    val cashPosition: Double get() = funding.cashPosition(payments.paid)

    /** Spend per head, or null when nobody has been counted. */
    val costPerGuest: Double? get() = guestCount?.takeIf { it > 0 }?.let { totalSpent / it }

    /** What the plan works out to per head — the figure to negotiate a caterer against. */
    val plannedPerGuest: Double? get() = guestCount?.takeIf { it > 0 }?.let { totalPlanned / it }

    /** The whole budget per head, whether or not it has been spent or planned yet. */
    val budgetPerGuest: Double? get() = guestCount?.takeIf { it > 0 }?.let { budget / it }
    /** Passing the total is a failure on a budget and the whole point of a goal. */
    val isOverBudget: Boolean get() = totalSpent > budget && !isSavingsGoal
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
    val topCategories: List<CategoryBreakdown>,
    /**
     * The currency every total below is expressed in.
     *
     * Budgets are each kept in their own currency, and a dollar goal plus a naira month
     * is not a number. So every figure that spans budgets is converted into the app's
     * base currency on the way into the sum, which is the unit the rest of the app
     * assumes an unqualified amount is in — meaning these totals format the ordinary
     * way, with no special handling at the call site.
     */
    val base: AppCurrency = AppCurrency.DEFAULT,
    /** The table those conversions are done with, carried so this stays a pure value. */
    val rates: FxRates = FxRates.BUNDLED
) {
    /**
     * The budgets money actually leaves, which is what every money figure below sums.
     *
     * A savings goal's total is a target and its remainder is a shortfall — how much
     * more has to be found. Summed in with the rest they would inflate the total budget
     * with money the user does not have, and report that gap as cash still available to
     * spend. [events] stays complete, because the per-budget list on Insights is a list
     * of everything being tracked.
     */
    private val spending: List<EventAnalytics> get() = events.filterNot { it.isSavingsGoal }

    /** One figure from each spending budget, converted into [base] and added up. */
    private fun sumInBase(figure: (EventAnalytics) -> Double): Double =
        spending.sumOf { rates.convert(figure(it), it.currency, base) }

    val totalBudget: Double get() = sumInBase { it.budget }
    val totalSpent: Double get() = sumInBase { it.totalSpent }
    val totalRemaining: Double get() = totalBudget - totalSpent
    val totalPaid: Double get() = sumInBase { it.totalPaid }
    val totalOutstanding: Double get() = sumInBase { it.outstanding }
    val totalOverdue: Double get() = sumInBase { it.payments.overdueAmount }
    val overdueCount: Int get() = spending.sumOf { it.payments.overdueCount }
    val totalReceived: Double get() = sumInBase { it.funding.received }
    val totalPledged: Double get() = sumInBase { it.funding.pledged }

    /** Whether any tracked budget is kept in something other than [base]. */
    val hasMixedCurrencies: Boolean get() = events.any { it.currency != base }

    /** Everything being tracked, savings goals included — this one counts rows. */
    val eventCount: Int get() = events.size
    val overBudgetCount: Int get() = events.count { it.isOverBudget }
    val percentUsed: Float
        get() = if (totalBudget > 0) (totalSpent / totalBudget).toFloat() else 0f

    /** Savings goals, for a caller that wants to show them rather than exclude them. */
    val savingsGoals: List<EventAnalytics> get() = events.filter { it.isSavingsGoal }
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
        contributions: List<ContributionEntity> = emptyList(),
        now: Long = System.currentTimeMillis(),
        /** What a budget with no currency of its own is denominated in. */
        base: AppCurrency = AppCurrency.DEFAULT
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
            daysRemaining = daysRemaining,
            payments = Payments.summarize(eventExpenses, now),
            funding = Funding.summarize(contributions.filter { it.eventId == event.id }),
            guestCount = event.guestCount,
            direction = event.direction,
            currency = event.currency(base)
        )
    }

    /** Whole days from [from] to [to], counting both ends. Negative spans collapse to 0. */
    private fun daysInclusive(from: Long, to: Long): Int =
        (((to - from) / MILLIS_PER_DAY) + 1).toInt().coerceAtLeast(0)

    fun portfolio(
        events: List<EventEntity>,
        expenses: List<ExpenseEntity>,
        budgetLines: List<BudgetLineEntity>,
        contributions: List<ContributionEntity> = emptyList(),
        now: Long = System.currentTimeMillis(),
        base: AppCurrency = AppCurrency.DEFAULT,
        rates: FxRates = FxRates.BUNDLED
    ): PortfolioAnalytics {
        val perEvent = events.map { forEvent(it, expenses, budgetLines, contributions, now, base) }

        // "Where the money goes" is about money going out. A savings goal's categories
        // are sources it comes *from*, and listing them here would rank "Side Income"
        // among the things the user spends on.
        // Pooled across budgets, so each one's figures are converted into the base
        // currency first — "Food & Groceries" is one row whether it was spent in naira
        // or in dollars, and it can only be one row if it is one unit.
        val topCategories = perEvent
            .filterNot { it.isSavingsGoal }
            .flatMap { event ->
                event.categories.map {
                    it.copy(
                        planned = rates.convert(it.planned, event.currency, base),
                        actual = rates.convert(it.actual, event.currency, base)
                    )
                }
            }
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

        return PortfolioAnalytics(
            events = perEvent,
            topCategories = topCategories,
            base = base,
            rates = rates
        )
    }

}
