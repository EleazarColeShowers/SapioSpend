package com.el.sapiospend

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.search.ExpenseSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseSearchTest {

    private fun expense(
        title: String,
        category: String = "Food",
        notes: String = "",
        amount: Double = 1000.0
    ) = ExpenseEntity(
        eventId = "event-1",
        title = title,
        category = category,
        amount = amount,
        notes = notes
    )

    private val expenses = listOf(
        expense("Catering deposit", category = "Catering", notes = "Paid to Chidi"),
        expense("Venue balance", category = "Venue", notes = "Final instalment"),
        expense("DJ booking", category = "Entertainment"),
        expense("Extra chairs", category = "Venue", notes = "40 chairs")
    )

    @Test
    fun `blank query returns everything`() {
        assertEquals(expenses, ExpenseSearch.filter(expenses, ""))
        assertEquals(expenses, ExpenseSearch.filter(expenses, "   "))
    }

    @Test
    fun `matches on title`() {
        val results = ExpenseSearch.filter(expenses, "venue balance")
        assertEquals(listOf("Venue balance"), results.map { it.title })
    }

    @Test
    fun `matches on category`() {
        val results = ExpenseSearch.filter(expenses, "Venue")
        assertEquals(listOf("Venue balance", "Extra chairs"), results.map { it.title })
    }

    /** The field somebody is least likely to remember having used, so it must be covered. */
    @Test
    fun `matches on notes`() {
        val results = ExpenseSearch.filter(expenses, "chidi")
        assertEquals(listOf("Catering deposit"), results.map { it.title })
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(1, ExpenseSearch.filter(expenses, "DJ").size)
        assertEquals(1, ExpenseSearch.filter(expenses, "dj").size)
        assertEquals(1, ExpenseSearch.filter(expenses, "Dj").size)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(
            ExpenseSearch.filter(expenses, "chairs"),
            ExpenseSearch.filter(expenses, "  chairs  ")
        )
    }

    @Test
    fun `a partial word matches`() {
        val results = ExpenseSearch.filter(expenses, "cater")
        assertEquals(listOf("Catering deposit"), results.map { it.title })
    }

    @Test
    fun `no match returns empty rather than everything`() {
        assertEquals(emptyList<ExpenseEntity>(), ExpenseSearch.filter(expenses, "photography"))
    }

    /**
     * The list arrives newest-first and the user is reading it in that order; matches
     * must not be re-ranked underneath them.
     */
    @Test
    fun `results keep their original order`() {
        val results = ExpenseSearch.filter(expenses, "e")
        assertEquals(
            expenses.filter { it in results }.map { it.title },
            results.map { it.title }
        )
    }
}
