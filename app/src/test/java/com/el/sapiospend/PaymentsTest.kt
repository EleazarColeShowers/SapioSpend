package com.el.sapiospend

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.payment.PaymentStatus
import com.el.sapiospend.domain.payment.Payments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun expense(
        amount: Double = 100_000.0,
        paid: Double = 0.0,
        due: Long? = null,
        id: String = "x1"
    ) = ExpenseEntity(
        id = id,
        eventId = "e1",
        title = "Catering",
        category = "Food",
        amount = amount,
        amountPaid = paid,
        dueDate = due
    )

    @Test
    fun `nothing paid is unpaid, part paid is a deposit, all paid is paid`() {
        assertEquals(PaymentStatus.UNPAID, Payments.statusOf(expense(paid = 0.0)))
        assertEquals(PaymentStatus.PARTIAL, Payments.statusOf(expense(paid = 30_000.0)))
        assertEquals(PaymentStatus.PAID, Payments.statusOf(expense(paid = 100_000.0)))
    }

    @Test
    fun `overpaying still reads as paid rather than falling through to a deposit`() {
        assertEquals(PaymentStatus.PAID, Payments.statusOf(expense(paid = 120_000.0)))
        assertEquals(0.0, expense(paid = 120_000.0).outstanding, 0.01)
    }

    @Test
    fun `an expense is overdue only once its date has passed with money still owed`() {
        assertTrue(Payments.isOverdue(expense(paid = 10_000.0, due = now - day), now))
        assertFalse("not yet due", Payments.isOverdue(expense(due = now + day), now))
        assertFalse("settled", Payments.isOverdue(expense(paid = 100_000.0, due = now - day), now))
        assertFalse("no deadline was ever agreed", Payments.isOverdue(expense(paid = 0.0, due = null), now))
    }

    @Test
    fun `a payment due today is not overdue, whatever time of day it is read`() {
        // The recurring charge that materialises this morning is due today, and a
        // comparison by instant would have it red by lunchtime.
        val dueEarlierToday = now - (2 * 60 * 60 * 1000)

        assertFalse(Payments.isOverdue(expense(due = dueEarlierToday), now))
        assertTrue("yesterday's is another matter", Payments.isOverdue(expense(due = now - day), now))
    }

    @Test
    fun `past due is measured in days so the card and the rows agree`() {
        assertFalse(Payments.isPastDue(now - (2 * 60 * 60 * 1000), now))
        assertTrue(Payments.isPastDue(now - day, now))
        assertFalse(Payments.isPastDue(now + day, now))
    }

    @Test
    fun `a summary separates what is committed from what has actually gone`() {
        val summary = Payments.summarize(
            listOf(
                expense(id = "a", amount = 500_000.0, paid = 500_000.0),
                expense(id = "b", amount = 300_000.0, paid = 100_000.0, due = now - day),
                expense(id = "c", amount = 200_000.0, paid = 0.0, due = now + (3 * day))
            ),
            now
        )

        assertEquals(1_000_000.0, summary.committed, 0.01)
        assertEquals(600_000.0, summary.paid, 0.01)
        assertEquals(400_000.0, summary.outstanding, 0.01)
        assertEquals(2, summary.unsettledCount)
        assertEquals(1, summary.overdueCount)
        assertEquals(200_000.0, summary.overdueAmount, 0.01)
        assertEquals(now - day, summary.nextDueDate)
    }

    @Test
    fun `an empty summary reports zeroes rather than failing`() {
        val summary = Payments.summarize(emptyList(), now)

        assertEquals(0.0, summary.committed, 0.01)
        assertEquals(0, summary.unsettledCount)
        assertNull(summary.nextDueDate)
    }

    @Test
    fun `marking paid settles the whole amount even after the total was corrected upwards`() {
        val corrected = expense(amount = 250_000.0, paid = 100_000.0)

        val settled = Payments.applyStatus(corrected, PaymentStatus.PAID)

        assertEquals(250_000.0, settled.amountPaid, 0.01)
        assertEquals(0.0, settled.outstanding, 0.01)
    }

    @Test
    fun `a deposit larger than the amount is clamped rather than stored as an overpayment`() {
        val result = Payments.applyStatus(expense(amount = 100_000.0), PaymentStatus.PARTIAL, deposit = 400_000.0)

        assertEquals(100_000.0, result.amountPaid, 0.01)
    }

    @Test
    fun `marking unpaid clears a deposit`() {
        val result = Payments.applyStatus(expense(paid = 40_000.0), PaymentStatus.UNPAID)

        assertEquals(0.0, result.amountPaid, 0.01)
        assertEquals(PaymentStatus.UNPAID, Payments.statusOf(result))
    }
}
