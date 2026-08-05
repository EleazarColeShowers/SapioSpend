package com.example.sapiospend.navigation

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

    /** Form screen for adding an expense to an event. */
    data object AddExpense : Routes("add_expense/{eventId}") {
        fun createRoute(eventId: String) = "add_expense/$eventId"
    }
}
