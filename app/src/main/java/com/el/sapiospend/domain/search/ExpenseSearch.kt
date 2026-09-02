package com.el.sapiospend.domain.search

import com.el.sapiospend.data.local.ExpenseEntity

/**
 * Free-text matching over a list of expenses.
 *
 * Pure and Android-free like the rest of the domain layer, so the matching rules are
 * provable in a plain JVM test rather than only observable by typing into a screen.
 */
object ExpenseSearch {

    /**
     * Expenses matching [query], in the order they were given.
     *
     * Title, category, vendor and notes are searched together rather than behind a field
     * picker: somebody hunting for a payment remembers "Chidi" or "deposit", not which of
     * the four fields they typed it into.
     *
     * Order is preserved rather than ranked by relevance. The list arrives newest-first
     * and that is the ordering the user is reading it in — re-sorting matches by how well
     * they scored would shuffle the dates, and for a list of expenses the date is the
     * thing that makes one row distinguishable from the next.
     *
     * A blank query returns everything, so an empty search box is not a filter.
     */
    fun filter(expenses: List<ExpenseEntity>, query: String): List<ExpenseEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return expenses
        return expenses.filter { matches(it, trimmed) }
    }

    /**
     * Whether one expense answers [query].
     *
     * Public so the global search can apply the same rules rather than restating them —
     * two searches that disagree about what "Chidi" matches is a bug nobody would report,
     * they would just stop trusting the search.
     */
    fun matches(expense: ExpenseEntity, query: String): Boolean =
        expense.title.contains(query, ignoreCase = true) ||
            expense.category.contains(query, ignoreCase = true) ||
            expense.vendor.contains(query, ignoreCase = true) ||
            expense.notes.contains(query, ignoreCase = true)
}
