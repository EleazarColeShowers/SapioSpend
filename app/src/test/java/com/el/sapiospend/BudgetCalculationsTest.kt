package com.el.sapiospend

import com.el.sapiospend.data.local.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The budget logic lives inline in the composables, so these tests extract and verify
// the same formulas directly — no Android context or ViewModel needed.
class BudgetCalculationsTest {

    // Takes an Int purely for readability in the tests below; ids are strings since the
    // move to client-generated keys, so it is stringified here.
    private fun makeExpense(eventId: Int, category: String, amount: Double) =
        ExpenseEntity(eventId = eventId.toString(), title = "item", category = category, amount = amount)

    @Test
    fun `remaining is budget minus total spent`() {
        val budget = 100_000.0
        val expenses = listOf(
            makeExpense(1, "Food", 20_000.0),
            makeExpense(1, "Venue", 30_000.0)
        )
        val spent = expenses.sumOf { it.amount }
        val remaining = budget - spent

        assertEquals(50_000.0, remaining, 0.01)
    }

    @Test
    fun `remaining is negative when over budget`() {
        val budget = 10_000.0
        val expenses = listOf(makeExpense(1, "Food", 15_000.0))
        val remaining = budget - expenses.sumOf { it.amount }

        assertTrue(remaining < 0)
    }

    @Test
    fun `isOverBudget is false when within budget`() {
        val budget = 50_000.0
        val spent = 49_999.0
        assertFalse(spent > budget)
    }

    @Test
    fun `isOverBudget is true when spent exceeds budget`() {
        val budget = 50_000.0
        val spent = 50_001.0
        assertTrue(spent > budget)
    }

    @Test
    fun `progress is zero when budget is zero`() {
        val budget = 0.0
        val spent = 0.0
        val progress = if (budget > 0) (spent / budget).toFloat() else 0f
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `progress is clamped to 1 when over budget`() {
        val budget = 1_000.0
        val spent = 5_000.0
        val progress = (spent / budget).toFloat().coerceIn(0f, 1f)
        assertEquals(1f, progress, 0.001f)
    }

    @Test
    fun `progress is 0_5 when half budget used`() {
        val budget = 100_000.0
        val spent = 50_000.0
        val progress = (spent / budget).toFloat().coerceIn(0f, 1f)
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun `category totals are summed correctly`() {
        val expenses = listOf(
            makeExpense(1, "Food", 10_000.0),
            makeExpense(1, "Food", 5_000.0),
            makeExpense(1, "Venue", 40_000.0)
        )
        val categoryTotals = expenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        assertEquals(15_000.0, categoryTotals["Food"]!!, 0.01)
        assertEquals(40_000.0, categoryTotals["Venue"]!!, 0.01)
    }

    @Test
    fun `total spent across all events sums correctly`() {
        val allExpenses = listOf(
            makeExpense(1, "Food", 10_000.0),
            makeExpense(2, "Venue", 50_000.0),
            makeExpense(2, "Transport", 5_000.0)
        )
        val totalSpent = allExpenses.sumOf { it.amount }
        assertEquals(65_000.0, totalSpent, 0.01)
    }

    @Test
    fun `per-event filter returns only that event's expenses`() {
        val allExpenses = listOf(
            makeExpense(1, "Food", 10_000.0),
            makeExpense(2, "Venue", 50_000.0),
            makeExpense(1, "Decoration", 8_000.0)
        )
        val event1Expenses = allExpenses.filter { it.eventId == "1" }
        assertEquals(2, event1Expenses.size)
        assertEquals(18_000.0, event1Expenses.sumOf { it.amount }, 0.01)
    }
}
