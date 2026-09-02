package com.el.sapiospend.domain.search

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.payment.PaymentStatus
import com.el.sapiospend.domain.payment.Payments

/** How the expense list is ordered. */
enum class ExpenseSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST("Highest"),
    LOWEST("Lowest"),
    DUE_SOONEST("Due soonest"),
    CATEGORY("Category")
}

/** Which expenses the list is narrowed to. */
enum class ExpenseStatusFilter(val label: String) {
    ALL("All"),
    OUTSTANDING("Owing"),
    OVERDUE("Overdue"),
    PAID("Paid")
}

/**
 * Everything the expense list is currently filtered and sorted by.
 *
 * One value rather than three pieces of screen state, so the list is derived from a
 * single object and a saved view — a widget, a report, a future "show me what's overdue"
 * link — can be expressed as one of these rather than as a set of arguments.
 */
data class ExpenseView(
    val query: String = "",
    val sort: ExpenseSort = ExpenseSort.NEWEST,
    val status: ExpenseStatusFilter = ExpenseStatusFilter.ALL,
    /** Null means every category. */
    val category: String? = null
) {
    val isNarrowed: Boolean
        get() = query.isNotBlank() || status != ExpenseStatusFilter.ALL || category != null
}

/**
 * Applies an [ExpenseView] to a list of expenses.
 *
 * Search, filter and sort in one pass and in one place, because the order matters:
 * filtering before sorting keeps the comparison cheap, and doing it in the composable
 * would put three separate `remember` blocks in the way of a rule that is one sentence
 * long. Pure, so the rules are unit tests.
 */
object ExpenseListing {

    fun apply(
        expenses: List<ExpenseEntity>,
        view: ExpenseView,
        now: Long = System.currentTimeMillis()
    ): List<ExpenseEntity> {
        val matched = ExpenseSearch.filter(expenses, view.query)
        val filtered = matched.filter { expense ->
            (view.category == null || expense.category == view.category) &&
                when (view.status) {
                    ExpenseStatusFilter.ALL -> true
                    ExpenseStatusFilter.OUTSTANDING -> !expense.isSettled
                    ExpenseStatusFilter.OVERDUE -> Payments.isOverdue(expense, now)
                    ExpenseStatusFilter.PAID -> Payments.statusOf(expense) == PaymentStatus.PAID
                }
        }
        return sort(filtered, view.sort)
    }

    private fun sort(expenses: List<ExpenseEntity>, sort: ExpenseSort): List<ExpenseEntity> =
        when (sort) {
            ExpenseSort.NEWEST -> expenses.sortedByDescending { it.dateCreated }
            ExpenseSort.OLDEST -> expenses.sortedBy { it.dateCreated }
            ExpenseSort.HIGHEST -> expenses.sortedByDescending { it.amount }
            ExpenseSort.LOWEST -> expenses.sortedBy { it.amount }
            // Undated lines sort last rather than first: "due soonest" is a question
            // about deadlines, and an expense with no deadline is not the answer to it.
            ExpenseSort.DUE_SOONEST -> expenses.sortedWith(
                compareBy<ExpenseEntity> { it.dueDate ?: Long.MAX_VALUE }.thenByDescending { it.dateCreated }
            )
            ExpenseSort.CATEGORY -> expenses.sortedWith(
                compareBy<ExpenseEntity> { it.category.lowercase() }.thenByDescending { it.amount }
            )
        }

    /** The categories present in [expenses], for the filter row to offer. */
    fun categoriesOf(expenses: List<ExpenseEntity>): List<String> =
        expenses.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
}
