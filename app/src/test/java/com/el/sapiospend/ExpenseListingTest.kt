package com.el.sapiospend

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.search.ExpenseListing
import com.el.sapiospend.domain.search.ExpenseSort
import com.el.sapiospend.domain.search.ExpenseStatusFilter
import com.el.sapiospend.domain.search.ExpenseView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseListingTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun expense(
        id: String,
        title: String = "item",
        category: String = "Food",
        vendor: String = "",
        amount: Double = 100_000.0,
        paid: Double = 100_000.0,
        due: Long? = null,
        date: Long = now
    ) = ExpenseEntity(
        id = id,
        eventId = "e1",
        title = title,
        category = category,
        vendor = vendor,
        amount = amount,
        amountPaid = paid,
        dueDate = due,
        dateCreated = date
    )

    private val expenses = listOf(
        expense("a", title = "Catering", vendor = "Chidi Catering", amount = 500_000.0, paid = 200_000.0, due = now - day, date = now - (2 * day)),
        expense("b", title = "Venue", category = "Venue", amount = 800_000.0, paid = 800_000.0, date = now - day),
        expense("c", title = "Cake", amount = 90_000.0, paid = 0.0, due = now + (5 * day), date = now)
    )

    @Test
    fun `the default view returns everything, newest first`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(), now)

        assertEquals(listOf("c", "b", "a"), result.map { it.id })
    }

    @Test
    fun `searching matches the vendor, not just the title`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(query = "chidi"), now)

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `the owing filter keeps deposits and unpaid lines and drops settled ones`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(status = ExpenseStatusFilter.OUTSTANDING), now)

        assertEquals(setOf("a", "c"), result.map { it.id }.toSet())
    }

    @Test
    fun `the overdue filter is narrower than owing`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(status = ExpenseStatusFilter.OVERDUE), now)

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `the paid filter keeps only fully settled lines`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(status = ExpenseStatusFilter.PAID), now)

        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `a category filter and a query narrow together rather than replacing each other`() {
        val result = ExpenseListing.apply(
            expenses,
            ExpenseView(query = "ca", category = "Food"),
            now
        )

        assertEquals(setOf("a", "c"), result.map { it.id }.toSet())
    }

    @Test
    fun `sorting by amount orders on the committed total`() {
        val highest = ExpenseListing.apply(expenses, ExpenseView(sort = ExpenseSort.HIGHEST), now)
        val lowest = ExpenseListing.apply(expenses, ExpenseView(sort = ExpenseSort.LOWEST), now)

        assertEquals(listOf("b", "a", "c"), highest.map { it.id })
        assertEquals(listOf("c", "a", "b"), lowest.map { it.id })
    }

    @Test
    fun `due soonest puts dated lines first and undated ones last`() {
        val result = ExpenseListing.apply(expenses, ExpenseView(sort = ExpenseSort.DUE_SOONEST), now)

        assertEquals(listOf("a", "c", "b"), result.map { it.id })
    }

    @Test
    fun `categories are offered from what is actually there`() {
        assertEquals(listOf("Food", "Venue"), ExpenseListing.categoriesOf(expenses))
    }

    @Test
    fun `a plain view is not narrowed and a filtered one is`() {
        assertFalse(ExpenseView().isNarrowed)
        assertTrue(ExpenseView(status = ExpenseStatusFilter.OVERDUE).isNarrowed)
        assertTrue(ExpenseView(query = "cake").isNarrowed)
        // Sorting is not narrowing: the same rows are on screen, in another order.
        assertFalse(ExpenseView(sort = ExpenseSort.HIGHEST).isNarrowed)
    }
}
