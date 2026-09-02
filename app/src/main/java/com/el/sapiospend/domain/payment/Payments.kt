package com.el.sapiospend.domain.payment

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.util.DateUtils

/**
 * Where one expense stands between booked and settled.
 *
 * Three states rather than a paid flag, because the middle one is where event money
 * actually lives: almost every vendor is booked with a deposit and settled later, and an
 * app that can only say "paid" or "not paid" has to call a half-paid caterer one or the
 * other. Overdue is deliberately *not* a fourth state — it is a fact about the due date,
 * true of an unpaid and a part-paid line alike, and folding it in here would make it
 * impossible to say "part paid, and late".
 */
enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIAL("Deposit paid"),
    PAID("Paid")
}

/**
 * The committed-versus-paid picture for a set of expenses.
 *
 * [committed] is what the budget has to answer for; [paid] is what has left the account;
 * [outstanding] is the bill still to come. A planner reading only one of the three would
 * be told either that they are broke or that they are fine, depending which.
 */
data class PaymentSummary(
    val committed: Double,
    val paid: Double,
    val outstanding: Double,
    val unsettledCount: Int,
    val overdueCount: Int,
    val overdueAmount: Double,
    /** The soonest unmet due date, for the "next payment" line. Null when nothing is dated. */
    val nextDueDate: Long?
)

/**
 * Payment maths, pure and Android-free like the rest of the domain layer.
 */
object Payments {

    /** Nothing committed and nothing owed, for a caller with no expenses to summarise. */
    val EMPTY = PaymentSummary(
        committed = 0.0,
        paid = 0.0,
        outstanding = 0.0,
        unsettledCount = 0,
        overdueCount = 0,
        overdueAmount = 0.0,
        nextDueDate = null
    )

    fun statusOf(expense: ExpenseEntity): PaymentStatus = when {
        expense.amountPaid >= expense.amount -> PaymentStatus.PAID
        expense.amountPaid > 0 -> PaymentStatus.PARTIAL
        else -> PaymentStatus.UNPAID
    }

    /**
     * Past its due date with money still owed.
     *
     * Compared by day, not by instant. A payment due today is due today: an instant
     * comparison would call it overdue from one second past the moment it was recorded,
     * and a charge would go red on the very morning it fell due. It becomes overdue when
     * the day it was due has passed.
     *
     * A settled expense is never overdue however old its date is, and an expense with no
     * due date never becomes overdue on its own — nobody agreed to a deadline, so the app
     * does not get to invent one.
     */
    fun isOverdue(expense: ExpenseEntity, now: Long = System.currentTimeMillis()): Boolean =
        expense.dueDate?.let { isPastDue(it, now) && expense.outstanding > 0 } == true

    /**
     * Whether a due date has been missed, ignoring what is owed against it.
     *
     * The date half of [isOverdue], for callers holding a date rather than an expense —
     * the "next payment due" line on the overview, which must agree with the rows
     * underneath it about what counts as late.
     */
    fun isPastDue(dueDate: Long, now: Long = System.currentTimeMillis()): Boolean =
        DateUtils.startOfDay(dueDate) < DateUtils.startOfDay(now)

    fun summarize(expenses: List<ExpenseEntity>, now: Long = System.currentTimeMillis()): PaymentSummary {
        val unsettled = expenses.filter { !it.isSettled }
        val overdue = unsettled.filter { isOverdue(it, now) }
        return PaymentSummary(
            committed = expenses.sumOf { it.amount },
            paid = expenses.sumOf { it.amountPaid },
            outstanding = expenses.sumOf { it.outstanding },
            unsettledCount = unsettled.size,
            overdueCount = overdue.size,
            overdueAmount = overdue.sumOf { it.outstanding },
            nextDueDate = unsettled.mapNotNull { it.dueDate }.minOrNull()
        )
    }

    /**
     * The expense as it should be stored after the user picks a status on the form.
     *
     * [PaymentStatus.PARTIAL] keeps whatever deposit is already recorded rather than
     * inventing a figure, and falls back to nothing paid when there is none — the form
     * asks for the amount separately. The other two are unambiguous, and are the reason
     * this exists at all: "Paid" has to mean paid *in full*, including after the total
     * has been edited upwards, or a corrected amount would silently leave a balance.
     */
    fun applyStatus(expense: ExpenseEntity, status: PaymentStatus, deposit: Double = 0.0): ExpenseEntity =
        when (status) {
            PaymentStatus.PAID -> expense.copy(amountPaid = expense.amount)
            PaymentStatus.UNPAID -> expense.copy(amountPaid = 0.0)
            PaymentStatus.PARTIAL -> expense.copy(
                amountPaid = deposit.coerceIn(0.0, expense.amount)
            )
        }
}
