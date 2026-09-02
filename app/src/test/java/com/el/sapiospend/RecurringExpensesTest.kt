package com.el.sapiospend

import com.el.sapiospend.data.local.RecurringExpenseEntity
import com.el.sapiospend.domain.recurring.Recurrence
import com.el.sapiospend.domain.recurring.RecurringExpenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringExpensesTest {

    private val day = 24L * 60 * 60 * 1000

    /** Midday on a fixed date, so nothing here sits on a day boundary. */
    private val start: Long = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 2, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun rule(
        due: Long = start,
        frequency: Recurrence = Recurrence.WEEKLY,
        until: Long? = null,
        active: Boolean = true,
        amount: Double = 50_000.0
    ) = RecurringExpenseEntity(
        id = "r1",
        eventId = "e1",
        title = "Venue hire",
        category = "Venue",
        amount = amount,
        frequency = frequency.name,
        nextDueDate = due,
        until = until,
        active = active
    )

    @Test
    fun `a rule due in the future charges nothing`() {
        val result = RecurringExpenses.materialize(listOf(rule(due = start + (7 * day))), now = start)

        assertTrue(result.isEmpty)
    }

    @Test
    fun `a rule due today charges once and moves to the next occurrence`() {
        val result = RecurringExpenses.materialize(listOf(rule()), now = start)

        assertEquals(1, result.expenses.size)
        assertEquals(50_000.0, result.expenses.first().amount, 0.01)
        assertEquals(start, result.expenses.first().dueDate)
        assertEquals(start + (7 * day), result.rules.single().nextDueDate)
    }

    @Test
    fun `a charge is materialised unpaid so it lands in what is still owed`() {
        val expense = RecurringExpenses.materialize(listOf(rule()), now = start).expenses.single()

        assertEquals(0.0, expense.amountPaid, 0.01)
        assertEquals(50_000.0, expense.outstanding, 0.01)
        assertEquals("Venue", expense.category)
        assertEquals("e1", expense.eventId)
    }

    @Test
    fun `weeks missed while the app was closed are all charged, dated to when they fell due`() {
        val result = RecurringExpenses.materialize(listOf(rule()), now = start + (21 * day))

        assertEquals(4, result.expenses.size)
        assertEquals(
            listOf(start, start + (7 * day), start + (14 * day), start + (21 * day)),
            result.expenses.map { it.dueDate }
        )
        assertEquals(start + (28 * day), result.rules.single().nextDueDate)
    }

    @Test
    fun `a rule stops at its end date and switches itself off`() {
        val result = RecurringExpenses.materialize(
            listOf(rule(until = start + (10 * day))),
            now = start + (30 * day)
        )

        // Two occurrences fall inside the window: the start day and a week later.
        assertEquals(2, result.expenses.size)
        assertFalse("an expired rule should not keep charging", result.rules.single().active)
    }

    @Test
    fun `a paused rule charges nothing even when it is overdue`() {
        val result = RecurringExpenses.materialize(
            listOf(rule(active = false)),
            now = start + (30 * day)
        )

        assertTrue(result.isEmpty)
    }

    @Test
    fun `a year of neglect is capped rather than inventing hundreds of charges`() {
        val result = RecurringExpenses.materialize(listOf(rule()), now = start + (700 * day))

        assertEquals(RecurringExpenses.MAX_CATCH_UP, result.expenses.size)
        // The rule is still fast-forwarded past today, so the next open of the app does
        // not produce another sixty.
        assertTrue(result.rules.single().nextDueDate > start + (700 * day))
    }

    @Test
    fun `a monthly rule advances by calendar month rather than thirty days`() {
        val monthly = rule(frequency = Recurrence.MONTHLY)

        val next = RecurringExpenses.materialize(listOf(monthly), now = start).rules.single().nextDueDate

        val calendar = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(Calendar.APRIL, calendar.get(Calendar.MONTH))
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a first charge in the past is pushed forward instead of back-charging`() {
        val firstDue = RecurringExpenses.firstDueDate(
            Recurrence.WEEKLY,
            start = start - (30 * day),
            now = start
        )

        assertTrue("must not fall before today", firstDue >= start)
    }

    @Test
    fun `a first charge in the future is left where the user put it`() {
        val chosen = start + (14 * day)

        assertEquals(chosen, RecurringExpenses.firstDueDate(Recurrence.WEEKLY, start = chosen, now = start))
    }

    @Test
    fun `an unrecognised stored frequency falls back rather than throwing`() {
        assertEquals(Recurrence.DEFAULT, Recurrence.fromName("QUARTERLY"))
        assertEquals(Recurrence.DEFAULT, Recurrence.fromName(null))
    }

    @Test
    fun `a rule set up today charges today, not next week`() {
        // The instant a rule is created is already in the past by the time anything
        // reads it. Compared as instants rather than as days, a rule started "today"
        // skips its first charge and silently jumps a week — which is exactly what it
        // did before this was compared by day.
        val createdAMinuteAgo = start - 60_000

        val firstDue = RecurringExpenses.firstDueDate(Recurrence.WEEKLY, start = createdAMinuteAgo, now = start)
        val result = RecurringExpenses.materialize(listOf(rule(due = firstDue)), now = start)

        assertEquals(createdAMinuteAgo, firstDue)
        assertEquals(1, result.expenses.size)
    }

    @Test
    fun `a charge lands on its due day whatever time of day the app happens to look`() {
        val dueAtMidday = start
        val checkedAtBreakfast = start - (4 * 60 * 60 * 1000)

        val result = RecurringExpenses.materialize(listOf(rule(due = dueAtMidday)), now = checkedAtBreakfast)

        assertEquals(1, result.expenses.size)
    }

}
