package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAnalyticsTest {

    private val day = 24L * 60 * 60 * 1000
    private val createdAt = 1_700_000_000_000L

    private fun event(id: String = "e1", budget: Double = 1_000_000.0) =
        EventEntity(id = id, name = "Wedding", budget = budget, eventType = "Wedding", dateCreated = createdAt)

    private fun expense(eventId: String, category: String, amount: Double) =
        ExpenseEntity(eventId = eventId, title = "item", category = category, amount = amount)

    private fun line(eventId: String, category: String, planned: Double) =
        BudgetLineEntity(eventId = eventId, category = category, plannedAmount = planned)

    @Test
    fun `spend and remaining are computed from the event's own expenses only`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(
                expense("e1", "Food", 300_000.0),
                expense("e1", "Venue", 200_000.0),
                expense("other", "Food", 999_000.0)
            ),
            emptyList(),
            createdAt
        )

        assertEquals(500_000.0, analytics.totalSpent, 0.01)
        assertEquals(500_000.0, analytics.remaining, 0.01)
        assertEquals(2, analytics.expenseCount)
        assertFalse(analytics.isOverBudget)
    }

    @Test
    fun `over budget is flagged and remaining goes negative`() {
        val analytics = BudgetAnalytics.forEvent(
            event(budget = 100_000.0),
            listOf(expense("e1", "Food", 150_000.0)),
            emptyList(),
            createdAt
        )

        assertTrue(analytics.isOverBudget)
        assertEquals(-50_000.0, analytics.remaining, 0.01)
    }

    @Test
    fun `categories union planned and actual`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 100_000.0), expense("e1", "Fireworks", 20_000.0)),
            listOf(line("e1", "Food", 80_000.0), line("e1", "Venue", 200_000.0)),
            createdAt
        )

        val byName = analytics.categories.associateBy { it.category }
        assertEquals(3, analytics.categories.size)

        // Planned and spent.
        assertEquals(80_000.0, byName.getValue("Food").planned, 0.01)
        assertEquals(20_000.0, byName.getValue("Food").variance, 0.01)
        assertTrue(byName.getValue("Food").isOverPlan)

        // Planned but untouched.
        assertEquals(0.0, byName.getValue("Venue").actual, 0.01)
        assertFalse(byName.getValue("Venue").isOverPlan)

        // Spent with no plan behind it.
        assertTrue(byName.getValue("Fireworks").isUnplanned)
    }

    @Test
    fun `biggest overrun picks the largest positive variance`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 150_000.0), expense("e1", "Decor", 120_000.0)),
            listOf(line("e1", "Food", 100_000.0), line("e1", "Decor", 50_000.0)),
            createdAt
        )

        assertEquals("Decor", analytics.biggestOverrun?.category)
        assertEquals(70_000.0, analytics.biggestOverrun?.variance!!, 0.01)
    }

    @Test
    fun `biggest overrun is null when everything is within plan`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 50_000.0)),
            listOf(line("e1", "Food", 100_000.0)),
            createdAt
        )

        assertNull(analytics.biggestOverrun)
    }

    @Test
    fun `unallocated budget is what the plan did not assign`() {
        val analytics = BudgetAnalytics.forEvent(
            event(budget = 1_000_000.0),
            emptyList(),
            listOf(line("e1", "Food", 600_000.0)),
            createdAt
        )

        assertEquals(400_000.0, analytics.unallocated, 0.01)
    }

    @Test
    fun `burn rate divides spend over days tracked`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 400_000.0)),
            emptyList(),
            createdAt + (3 * day)
        )

        assertEquals(4, analytics.daysTracked)
        assertEquals(100_000.0, analytics.dailyBurnRate, 0.01)
    }

    @Test
    fun `an event created moments ago counts as one day rather than dividing by zero`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 50_000.0)),
            emptyList(),
            createdAt
        )

        assertEquals(1, analytics.daysTracked)
        assertEquals(50_000.0, analytics.dailyBurnRate, 0.01)
        assertFalse(analytics.dailyBurnRate.isInfinite())
    }

    @Test
    fun `a zero budget event reports zero percent instead of NaN`() {
        val analytics = BudgetAnalytics.forEvent(
            event(budget = 0.0),
            listOf(expense("e1", "Food", 10_000.0)),
            emptyList(),
            createdAt
        )

        assertEquals(0f, analytics.percentUsed, 0.0001f)
        assertFalse(analytics.percentUsed.isNaN())
    }

    @Test
    fun `portfolio totals sum across every event`() {
        val portfolio = BudgetAnalytics.portfolio(
            listOf(event("e1", 1_000_000.0), event("e2", 500_000.0)),
            listOf(expense("e1", "Food", 400_000.0), expense("e2", "Food", 600_000.0)),
            emptyList(),
            createdAt
        )

        assertEquals(1_500_000.0, portfolio.totalBudget, 0.01)
        assertEquals(1_000_000.0, portfolio.totalSpent, 0.01)
        assertEquals(500_000.0, portfolio.totalRemaining, 0.01)
        assertEquals(2, portfolio.eventCount)
        assertEquals(1, portfolio.overBudgetCount)
    }

    @Test
    fun `portfolio merges the same category across events`() {
        val portfolio = BudgetAnalytics.portfolio(
            listOf(event("e1"), event("e2")),
            listOf(expense("e1", "Food", 400_000.0), expense("e2", "Food", 100_000.0)),
            emptyList(),
            createdAt
        )

        assertEquals(1, portfolio.topCategories.size)
        assertEquals(500_000.0, portfolio.topCategories.first().actual, 0.01)
    }

    @Test
    fun `portfolio omits planned categories with no spend`() {
        val portfolio = BudgetAnalytics.portfolio(
            listOf(event("e1")),
            emptyList(),
            listOf(line("e1", "Venue", 200_000.0)),
            createdAt
        )

        assertTrue(portfolio.topCategories.isEmpty())
    }

    @Test
    fun `empty portfolio reports zeroes rather than failing`() {
        val portfolio = BudgetAnalytics.portfolio(emptyList(), emptyList(), emptyList(), createdAt)

        assertEquals(0.0, portfolio.totalBudget, 0.01)
        assertEquals(0f, portfolio.percentUsed, 0.0001f)
        assertEquals(0, portfolio.eventCount)
    }

    // --- Budget periods ---------------------------------------------------------
    // A 30-day month starting the day the event was created, which is the shape a
    // salary earner's budget takes.

    private fun monthlyEvent(budget: Double = 300_000.0, start: Long = createdAt) =
        EventEntity(
            id = "e1",
            name = "August Salary",
            budget = budget,
            eventType = "Personal",
            dateCreated = createdAt,
            startDate = start,
            endDate = start + (29 * day)
        )

    @Test
    fun `an event with no dates has no period figures at all`() {
        val analytics = BudgetAnalytics.forEvent(
            event(),
            listOf(expense("e1", "Food", 100_000.0)),
            emptyList(),
            createdAt + (3 * day)
        )

        assertFalse(analytics.hasPeriod)
        assertNull(analytics.daysRemaining)
        assertNull(analytics.safeDailySpend)
        assertNull(analytics.projectedTotalSpend)
        assertNull(analytics.percentOfPeriodElapsed)
        // No calendar to outrun, so pace is never a complaint on an open-ended budget.
        assertFalse(analytics.isSpendingAheadOfPace)
    }

    @Test
    fun `period length days remaining and safe daily spend are computed mid-month`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(),
            listOf(expense("e1", "Food", 100_000.0)),
            emptyList(),
            createdAt + (9 * day)
        )

        assertEquals(30, analytics.periodLengthDays)
        assertEquals(10, analytics.daysTracked)
        assertEquals(20, analytics.daysRemaining)
        // 200,000 left spread over today plus the 20 days after it.
        assertEquals(200_000.0 / 21, analytics.safeDailySpend!!, 0.01)
        assertEquals(1f / 3f, analytics.percentOfPeriodElapsed!!, 0.001f)
    }

    @Test
    fun `spending faster than the month passes is flagged and projected forward`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(),
            listOf(expense("e1", "Food", 150_000.0)),
            emptyList(),
            createdAt + (9 * day)
        )

        // Half the money in a third of the month.
        assertTrue(analytics.isSpendingAheadOfPace)
        assertEquals(450_000.0, analytics.projectedTotalSpend!!, 0.01)
        assertEquals(150_000.0, analytics.projectedOverspend!!, 0.01)
    }

    @Test
    fun `spending in line with the calendar is not flagged and projects no overspend`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(),
            listOf(expense("e1", "Food", 50_000.0)),
            emptyList(),
            createdAt + (9 * day)
        )

        assertFalse(analytics.isSpendingAheadOfPace)
        assertEquals(150_000.0, analytics.projectedTotalSpend!!, 0.01)
        assertNull(analytics.projectedOverspend)
    }

    @Test
    fun `a closed period stops accruing days so the burn rate stays what it actually was`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(),
            listOf(expense("e1", "Food", 300_000.0)),
            emptyList(),
            // Two months after the budget closed.
            createdAt + (90 * day)
        )

        assertEquals(30, analytics.daysTracked)
        assertEquals(0, analytics.daysRemaining)
        assertTrue(analytics.isPeriodOver)
        assertEquals(10_000.0, analytics.dailyBurnRate, 0.01)
        assertEquals(1f, analytics.percentOfPeriodElapsed!!, 0.0001f)
    }

    @Test
    fun `a period that has not started yet has its whole length remaining`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(start = createdAt + (5 * day)),
            emptyList(),
            emptyList(),
            createdAt
        )

        assertEquals(30, analytics.daysRemaining)
        assertEquals(1, analytics.daysTracked)
        assertEquals(0.0, analytics.dailyBurnRate, 0.01)
        assertFalse(analytics.isPeriodOver)
    }

    @Test
    fun `an overspent period reports a negative daily allowance rather than pretending`() {
        val analytics = BudgetAnalytics.forEvent(
            monthlyEvent(),
            listOf(expense("e1", "Food", 400_000.0)),
            emptyList(),
            createdAt + (9 * day)
        )

        assertTrue(analytics.safeDailySpend!! < 0)
        assertTrue(analytics.isOverBudget)
    }

    @Test
    fun `an end date without a start runs from the event's creation`() {
        val analytics = BudgetAnalytics.forEvent(
            event().copy(endDate = createdAt + (9 * day)),
            emptyList(),
            emptyList(),
            createdAt + (2 * day)
        )

        assertEquals(10, analytics.periodLengthDays)
        assertEquals(7, analytics.daysRemaining)
    }
}
