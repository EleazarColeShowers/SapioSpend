package com.el.sapiospend.domain.recurring

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.data.local.RecurringExpenseEntity
import com.el.sapiospend.util.DateUtils

/**
 * The expenses a set of rules has come due for, and the rules as they should be written
 * back afterwards.
 *
 * Returned together so the caller writes both in one transaction: the advanced due date
 * is the only record that an occurrence has already been charged.
 */
data class Materialization(
    val expenses: List<ExpenseEntity> = emptyList(),
    val rules: List<RecurringExpenseEntity> = emptyList()
) {
    val isEmpty: Boolean get() = expenses.isEmpty() && rules.isEmpty()
}

/**
 * Turns recurring rules into real expenses as their dates arrive.
 *
 * Pure, so the awkward cases — a rule whose end date has passed, a phone that was off
 * for six weeks, a monthly rule started on the 31st — are unit tests rather than
 * something you find out by changing the system clock.
 */
object RecurringExpenses {

    /**
     * How many occurrences one rule may generate in a single pass.
     *
     * A phone left off for a year should not wake to four hundred invented charges it
     * has no receipts for. Past the cap the rule is fast-forwarded to its next future
     * date without charging for the skipped occurrences, and the user sees a budget that
     * is merely out of date rather than one full of fiction.
     */
    const val MAX_CATCH_UP = 60

    fun materialize(
        rules: List<RecurringExpenseEntity>,
        now: Long = System.currentTimeMillis()
    ): Materialization {
        val expenses = mutableListOf<ExpenseEntity>()
        val updated = mutableListOf<RecurringExpenseEntity>()

        rules.filter { it.active && it.deletedAt == null }.forEach { rule ->
            val recurrence = Recurrence.fromName(rule.frequency)
            var due = rule.nextDueDate
            var charged = 0
            var changed = false

            while (hasArrived(due, now) && rule.until?.let { due <= it } != false) {
                if (charged < MAX_CATCH_UP) {
                    expenses += ExpenseEntity(
                        eventId = rule.eventId,
                        title = rule.title,
                        category = rule.category,
                        amount = rule.amount,
                        vendor = rule.vendor,
                        // Materialised unpaid and dated to the day it fell due: the app
                        // knows the charge exists, not that anyone has settled it. It
                        // lands in the outstanding column until the user says otherwise.
                        amountPaid = 0.0,
                        dueDate = due,
                        dateCreated = due,
                        notes = "Recurring · ${recurrence.label.lowercase()}"
                    )
                    charged++
                }
                due = recurrence.next(due)
                changed = true
            }

            // Past its end date the rule is switched off rather than deleted, so the user
            // can see what used to run and turn it back on.
            val expired = rule.until?.let { due > it } == true
            if (changed || expired) {
                updated += rule.copy(
                    nextDueDate = due,
                    active = !expired,
                    updatedAt = now
                )
            }
        }

        return Materialization(expenses, updated)
    }

    /**
     * The first occurrence for a rule the user is creating.
     *
     * [start] as given when it is still ahead, otherwise the next occurrence after now —
     * a rule added today for a payment that went out last week should not immediately
     * charge for it. The user records that one as an ordinary expense.
     */
    fun firstDueDate(
        recurrence: Recurrence,
        start: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        var due = start
        var steps = 0
        while (isBeforeToday(due, now) && steps < MAX_CATCH_UP) {
            due = recurrence.next(due)
            steps++
        }
        return due
    }

    /**
     * Whether [due] has arrived, compared by day rather than by instant.
     *
     * This is the difference between a rule that works and one that quietly skips its
     * first charge. A rule set up at 10:01 for "today" stores 10:01, and by the time
     * anything reads it the instant is already in the past — but on an instant
     * comparison a rule set up at 10:01 and read at 10:00 the same morning would be
     * "not due yet" and jump a whole week. Budgets run on days, so the comparison does
     * too: the charge lands the day it falls due, whatever o'clock either side is.
     */
    private fun hasArrived(due: Long, now: Long): Boolean =
        DateUtils.startOfDay(due) <= DateUtils.startOfDay(now)

    private fun isBeforeToday(due: Long, now: Long): Boolean =
        DateUtils.startOfDay(due) < DateUtils.startOfDay(now)
}
