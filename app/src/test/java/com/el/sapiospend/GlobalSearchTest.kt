package com.el.sapiospend

import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.search.GlobalSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {

    private fun event(id: String, name: String, type: String = "Wedding") =
        EventEntity(id = id, name = name, budget = 1_000_000.0, eventType = type)

    private fun expense(id: String, eventId: String, title: String, vendor: String = "") =
        ExpenseEntity(id = id, eventId = eventId, title = title, category = "Food", amount = 1.0, vendor = vendor)

    private val events = listOf(event("e1", "Tolu & Ada"), event("e2", "August Salary", type = "Personal"))
    private val expenses = listOf(
        expense("x1", "e1", "Catering", vendor = "Chidi Catering"),
        expense("x2", "e2", "Groceries")
    )

    @Test
    fun `a blank query returns nothing rather than everything`() {
        val results = GlobalSearch.search(events, expenses, "   ")

        assertTrue(results.isEmpty)
    }

    @Test
    fun `a query spans events and the expenses inside them`() {
        val results = GlobalSearch.search(events, expenses, "cater")

        assertTrue(results.events.isEmpty())
        assertEquals(listOf("x1"), results.expenses.map { it.expense.id })
        // The hit carries its event, so a result is placeable without a second lookup.
        assertEquals("Tolu & Ada", results.expenses.first().eventName)
    }

    @Test
    fun `an event matches on its name and on its type`() {
        assertEquals(listOf("e1"), GlobalSearch.search(events, expenses, "tolu").events.map { it.id })
        assertEquals(listOf("e2"), GlobalSearch.search(events, expenses, "personal").events.map { it.id })
    }

    @Test
    fun `an expense whose event is gone is not offered, since there is nowhere to open it`() {
        val orphan = expense("x9", "deleted-event", "Catering")

        val results = GlobalSearch.search(events, expenses + orphan, "catering")

        assertEquals(listOf("x1"), results.expenses.map { it.expense.id })
    }

    @Test
    fun `results are capped so a broad query cannot render a thousand rows`() {
        val many = (1..100).map { expense("x$it", "e1", "Deposit $it") }

        val results = GlobalSearch.search(events, many, "deposit")

        assertEquals(GlobalSearch.MAX_EXPENSE_HITS, results.expenses.size)
    }
}
