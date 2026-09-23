package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.domain.template.BudgetTemplates
import com.el.sapiospend.ui.text.BudgetWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The direction is persisted by name and read back by matching on it, so the two things
 * worth pinning down are that an unreadable value degrades to the safe answer, and that
 * the two vocabularies never share a string.
 */
class BudgetDirectionTest {

    @Test
    fun `an unrecognised stored value reads as spending rather than throwing`() {
        // A row written by a future build, or by a bug. Spending is the safe answer:
        // it shows the user more than a savings goal does, rather than hiding the
        // payment tracking on a budget that has bills in it.
        assertEquals(BudgetDirection.SPENDING, BudgetDirection.fromName("SOMETHING_ELSE"))
        assertEquals(BudgetDirection.SPENDING, BudgetDirection.fromName(null))
        assertEquals(BudgetDirection.SPENDING, BudgetDirection.fromName(""))
    }

    @Test
    fun `every name round-trips`() {
        BudgetDirection.entries.forEach { direction ->
            assertEquals(direction, BudgetDirection.fromName(direction.name))
        }
    }

    @Test
    fun `only the savings goal template flows money inwards`() {
        val saving = BudgetTemplates.all.filter { it.direction == BudgetDirection.SAVING }

        assertEquals(1, saving.size)
        assertEquals("savings_goal", saving.single().id)
    }

    @Test
    fun `a savings goal never speaks the spending vocabulary`() {
        val spending = BudgetWords.SPENDING
        val saving = BudgetWords.SAVING

        // Paired field by field: a set that shared even one id would put
        // "Contributions" next to "Remaining" on the same card. formSubtitleEdit is the
        // deliberate exception — correcting what you recorded is the same act either way.
        assertNotEquals(spending.total, saving.total)
        assertNotEquals(spending.spent, saving.spent)
        assertNotEquals(spending.remaining, saving.remaining)
        assertNotEquals(spending.left, saving.left)
        assertNotEquals(spending.entries, saving.entries)
        assertNotEquals(spending.addEntry, saving.addEntry)
        assertNotEquals(spending.formTitleNew, saving.formTitleNew)
        assertNotEquals(spending.formSubtitleNew, saving.formSubtitleNew)
        assertNotEquals(spending.amountHelp, saving.amountHelp)
        assertNotEquals(spending.saveEntry, saving.saveEntry)
        assertNotEquals(spending.categoriesTitle, saving.categoriesTitle)
        assertEquals(spending.formSubtitleEdit, saving.formSubtitleEdit)
    }

    @Test
    fun `only a spending budget shows funding and payment status`() {
        // Money arriving is not half-settled and cannot fall due. Showing those controls
        // on a savings goal is the bug this whole enum exists to fix.
        assertTrue(BudgetWords.of(BudgetDirection.SPENDING).showsFundingAndPayments)
        assertFalse(BudgetWords.of(BudgetDirection.SAVING).showsFundingAndPayments)
    }

    @Test
    fun `of maps each direction to its own word set`() {
        assertEquals(BudgetWords.SPENDING, BudgetWords.of(BudgetDirection.SPENDING))
        assertEquals(BudgetWords.SAVING, BudgetWords.of(BudgetDirection.SAVING))
    }

    // --- The portfolio must not spend a savings target ------------------------------

    private fun budget(id: String, total: Double, direction: BudgetDirection) =
        EventEntity(
            id = id,
            name = id,
            budget = total,
            eventType = "Personal",
            moneyDirection = direction.name
        )

    private fun entry(eventId: String, amount: Double) =
        ExpenseEntity(eventId = eventId, title = "item", category = "General", amount = amount)

    @Test
    fun `a savings target is left out of the portfolio totals`() {
        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(
                budget("month", 300_000.0, BudgetDirection.SPENDING),
                budget("laptop", 900_000.0, BudgetDirection.SAVING)
            ),
            expenses = listOf(
                entry("month", 120_000.0),
                entry("laptop", 200_000.0)
            ),
            budgetLines = emptyList<BudgetLineEntity>()
        )

        // Counting the laptop in would claim a ₦1,200,000 budget with ₦880,000 left to
        // spend, when the truth is a ₦300,000 month with ₦180,000 left and a goal
        // ₦700,000 short of its target.
        assertEquals(300_000.0, portfolio.totalBudget, 0.01)
        assertEquals(120_000.0, portfolio.totalSpent, 0.01)
        assertEquals(180_000.0, portfolio.totalRemaining, 0.01)

        // The list itself keeps everything: Insights shows a row per budget tracked.
        assertEquals(2, portfolio.eventCount)
        assertEquals(1, portfolio.savingsGoals.size)
        assertEquals("laptop", portfolio.savingsGoals.single().eventId)
    }

    @Test
    fun `passing the total is over budget for a budget and not for a goal`() {
        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(
                budget("month", 100_000.0, BudgetDirection.SPENDING),
                budget("laptop", 100_000.0, BudgetDirection.SAVING)
            ),
            // Both are past their total. Only one of them is in trouble.
            expenses = listOf(entry("month", 150_000.0), entry("laptop", 150_000.0)),
            budgetLines = emptyList<BudgetLineEntity>()
        )

        assertEquals(1, portfolio.overBudgetCount)
        assertTrue(portfolio.events.first { it.eventId == "month" }.isOverBudget)
        assertFalse(portfolio.events.first { it.eventId == "laptop" }.isOverBudget)
    }

    @Test
    fun `where the money goes ignores a savings goal's sources`() {
        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(
                budget("month", 300_000.0, BudgetDirection.SPENDING),
                budget("laptop", 900_000.0, BudgetDirection.SAVING)
            ),
            expenses = listOf(
                ExpenseEntity(eventId = "month", title = "Rent", category = "Rent", amount = 100_000.0),
                ExpenseEntity(eventId = "laptop", title = "October", category = "Side Income", amount = 200_000.0)
            ),
            budgetLines = emptyList<BudgetLineEntity>()
        )

        // "Side Income" is money arriving. Ranking it among what the user spends on is
        // how a savings goal would come to dominate the spending breakdown.
        assertEquals(listOf("Rent"), portfolio.topCategories.map { it.category })
    }
}
