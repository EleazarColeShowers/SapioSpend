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
     * Title, category and notes are searched together rather than behind a field picker:
     * somebody hunting for a payment remembers "Chidi" or "deposit", not which of the
     * three fields they typed it into.
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
        return expenses.filter { it.matches(trimmed) }
    }

    private fun ExpenseEntity.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            category.contains(query, ignoreCase = true) ||
            notes.contains(query, ignoreCase = true)
}
