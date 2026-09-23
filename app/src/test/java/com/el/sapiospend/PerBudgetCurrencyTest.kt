package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.BudgetMoney
import com.el.sapiospend.settings.FxRates
import com.el.sapiospend.util.formatAmountInput
import com.el.sapiospend.util.formatConverted
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.util.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A budget is kept in its own currency, and the two halves of that have to hold at once:
 * inside one budget nothing is ever converted, and across budgets everything is.
 *
 * The rates here are pinned rather than taken from the bundled table, so these tests say
 * what they mean arithmetically and do not change meaning the next time the table is
 * refreshed.
 */
class PerBudgetCurrencyTest {

    private val rates = FxRates(
        perUsd = mapOf("USD" to 1.0, "NGN" to 1000.0, "GBP" to 0.8),
        asOf = 0L,
        source = FxRates.Source.BUNDLED
    )

    private fun event(
        id: String,
        budget: Double,
        currency: AppCurrency?,
        direction: BudgetDirection = BudgetDirection.SPENDING
    ) = EventEntity(
        id = id,
        name = id,
        budget = budget,
        eventType = "Personal",
        moneyDirection = direction.name,
        currencyCode = currency?.code
    )

    private fun expense(eventId: String, amount: Double, category: String = "Food") =
        ExpenseEntity(eventId = eventId, title = "x", category = category, amount = amount)

    // --- Inside one budget -----------------------------------------------------------

    @Test
    fun `a budget's own figures are never converted, whatever the app is read in`() {
        val money = BudgetMoney(currency = AppCurrency.USD, display = AppCurrency.NGN, rates = rates)

        // Typed as 5000, stored as 5000, shown as $5,000 — the target somebody set does
        // not move because the naira did.
        assertEquals(5_000.0, "5000".parseAmount(money)!!, 0.0)
        assertEquals("$5,000", 5_000.0.formatMoney(money))
        assertEquals("5000", 5_000.0.formatAmountInput(money))
    }

    @Test
    fun `the converted line is an orientation, and absent when it would only repeat`() {
        val dollars = BudgetMoney(AppCurrency.USD, AppCurrency.NGN, rates)
        assertEquals("≈ ₦5,000,000", 5_000.0.formatConverted(dollars))

        // Read in the currency it is kept in, there is nothing to say.
        val naira = BudgetMoney(AppCurrency.NGN, AppCurrency.NGN, rates)
        assertNull(5_000.0.formatConverted(naira))
    }

    @Test
    fun `a budget saved before currencies were per-budget follows the app-wide base`() {
        val legacy = event("legacy", 600_000.0, currency = null)

        assertEquals(AppCurrency.NGN, legacy.currency(AppCurrency.NGN))
        assertEquals(AppCurrency.USD, legacy.currency(AppCurrency.USD))

        // And the analytics for it are reported in that same currency rather than in
        // whatever the enum happens to default to.
        val analytics = BudgetAnalytics.forEvent(legacy, emptyList(), emptyList(), base = AppCurrency.GBP)
        assertEquals(AppCurrency.GBP, analytics.currency)
    }

    // --- Across budgets --------------------------------------------------------------

    @Test
    fun `portfolio totals convert each budget into the base currency before summing`() {
        val naira = event("naira", 600_000.0, AppCurrency.NGN)
        val dollars = event("dollars", 1_000.0, AppCurrency.USD)

        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(naira, dollars),
            expenses = listOf(expense("naira", 100_000.0), expense("dollars", 250.0)),
            budgetLines = emptyList(),
            base = AppCurrency.NGN,
            rates = rates
        )

        // ₦600,000 + $1,000 at ₦1,000 to the dollar is ₦1,600,000 — not 601,000, which
        // is what adding the two stored figures would give.
        assertEquals(1_600_000.0, portfolio.totalBudget, 0.01)
        assertEquals(350_000.0, portfolio.totalSpent, 0.01)
        assertEquals(1_250_000.0, portfolio.totalRemaining, 0.01)
        assertTrue(portfolio.hasMixedCurrencies)
    }

    @Test
    fun `each budget still reports its own figures untouched`() {
        val dollars = event("dollars", 1_000.0, AppCurrency.USD)

        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(dollars),
            expenses = listOf(expense("dollars", 250.0)),
            budgetLines = emptyList(),
            base = AppCurrency.NGN,
            rates = rates
        )

        val analytics = portfolio.events.single()
        assertEquals(AppCurrency.USD, analytics.currency)
        assertEquals(1_000.0, analytics.budget, 0.0)
        assertEquals(250.0, analytics.totalSpent, 0.0)
        assertEquals(750.0, analytics.remaining, 0.0)
    }

    @Test
    fun `one category spent in two currencies is pooled as one row`() {
        val naira = event("naira", 600_000.0, AppCurrency.NGN)
        val dollars = event("dollars", 1_000.0, AppCurrency.USD)

        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(naira, dollars),
            expenses = listOf(
                expense("naira", 50_000.0, category = "Food"),
                expense("dollars", 100.0, category = "Food")
            ),
            budgetLines = listOf(
                BudgetLineEntity(eventId = "naira", category = "Food", plannedAmount = 80_000.0)
            ),
            base = AppCurrency.NGN,
            rates = rates
        )

        val food = portfolio.topCategories.single()
        assertEquals("Food", food.category)
        // ₦50,000 + $100 → ₦150,000. Left unconverted it would read ₦50,100 and make the
        // dollar spend invisible in the ranking.
        assertEquals(150_000.0, food.actual, 0.01)
        assertEquals(80_000.0, food.planned, 0.01)
    }

    @Test
    fun `a single-currency portfolio is exact and says nothing about conversion`() {
        val a = event("a", 600_000.0, AppCurrency.NGN)
        val b = event("b", 400_000.0, currency = null)

        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(a, b),
            expenses = emptyList(),
            budgetLines = emptyList(),
            base = AppCurrency.NGN,
            rates = rates
        )

        assertEquals(1_000_000.0, portfolio.totalBudget, 0.0)
        assertTrue(!portfolio.hasMixedCurrencies)
    }

    @Test
    fun `a savings goal in another currency stays out of the spending totals`() {
        val month = event("month", 600_000.0, AppCurrency.NGN)
        val goal = event("goal", 5_000.0, AppCurrency.USD, direction = BudgetDirection.SAVING)

        val portfolio = BudgetAnalytics.portfolio(
            events = listOf(month, goal),
            expenses = emptyList(),
            budgetLines = emptyList(),
            base = AppCurrency.NGN,
            rates = rates
        )

        // The goal's target is not money available to spend, in any currency.
        assertEquals(600_000.0, portfolio.totalBudget, 0.0)
        assertEquals(2, portfolio.eventCount)
        assertNotNull(portfolio.savingsGoals.single())
    }
}
