package com.el.sapiospend.domain.search

import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity

/** One expense found outside the event it belongs to, carrying enough context to place it. */
data class ExpenseHit(
    val expense: ExpenseEntity,
    val eventId: String,
    val eventName: String
)

data class GlobalResults(
    val events: List<EventEntity>,
    val expenses: List<ExpenseHit>
) {
    val isEmpty: Boolean get() = events.isEmpty() && expenses.isEmpty()
    val total: Int get() = events.size + expenses.size
}

/**
 * Search across every event and every expense at once.
 *
 * The event-scoped search on the detail screen answers "where in this budget", which is
 * only useful once you already know which budget. A planner asking "what did I pay
 * Chidi" does not know that — the whole point of the question is that it spans events.
 *
 * Pure and Android-free, and reusing [ExpenseSearch] rather than restating its rules, so
 * the two searches can never start disagreeing about what matches.
 */
object GlobalSearch {

    /** Expenses beyond this are not shown; a query matching hundreds is a query to narrow. */
    const val MAX_EXPENSE_HITS = 40

    fun search(
        events: List<EventEntity>,
        expenses: List<ExpenseEntity>,
        query: String
    ): GlobalResults {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return GlobalResults(emptyList(), emptyList())

        val namesById = events.associate { it.id to it.name }

        return GlobalResults(
            events = events.filter { it.matches(trimmed) },
            expenses = expenses
                // An expense whose event is gone has nowhere to navigate to, so it is
                // not offered as a result — a tombstoned event still has live-looking
                // rows until a future sync clears them.
                .filter { it.eventId in namesById && ExpenseSearch.matches(it, trimmed) }
                .take(MAX_EXPENSE_HITS)
                .map { ExpenseHit(it, it.eventId, namesById.getValue(it.eventId)) }
        )
    }

    private fun EventEntity.matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) || eventType.contains(query, ignoreCase = true)
}
