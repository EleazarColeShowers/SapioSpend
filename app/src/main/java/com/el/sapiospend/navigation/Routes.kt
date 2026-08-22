package com.el.sapiospend.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 * Each object holds the route string used by the Compose Navigation graph.
 *
 * Event ids are UUID strings rather than ints since the move to client-generated keys.
 */
sealed class Routes(val route: String) {
    /** Main screen listing all events and the overall budget overview. */
    data object Home : Routes("home")

    /** Form screen for creating a new event. */
    data object AddEvent : Routes("add_event")

    /** Portfolio-wide analytics across every event. */
    data object Analytics : Routes("analytics")

    /** Detail screen showing budget breakdown and expenses for a single event. */
    data object EventDetail : Routes("event_detail/{eventId}") {
        fun createRoute(eventId: String) = "event_detail/$eventId"
    }

    /** Editor for an event's planned category allocations. */
    data object BudgetPlan : Routes("budget_plan/{eventId}") {
        fun createRoute(eventId: String) = "budget_plan/$eventId"
    }

    /** Form screen for adding an expense to an event. */
    data object AddExpense : Routes("add_expense/{eventId}") {
        fun createRoute(eventId: String) = "add_expense/$eventId"
    }

    /**
     * The same form, opened on an expense that already exists. Only the expense id is
     * carried — which event it belongs to is a property of the expense, not of the
     * navigation, and passing both would let the two disagree.
     */
    data object EditExpense : Routes("edit_expense/{expenseId}") {
        fun createRoute(expenseId: String) = "edit_expense/$expenseId"
    }
}
