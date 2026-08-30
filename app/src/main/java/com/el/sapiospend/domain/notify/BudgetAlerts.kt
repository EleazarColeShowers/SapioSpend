package com.el.sapiospend.domain.notify

import com.el.sapiospend.domain.analytics.EventAnalytics

/**
 * The points on the way through a budget worth interrupting somebody for.
 *
 * Two, not five. Every extra threshold is another notification for the same event, and
 * the fastest way to make a budget alert worthless is to send four of them before the
 * money is actually gone.
 */
enum class BudgetThreshold(val fraction: Float) {
    /** Enough left to change course, little enough that it matters. */
    WARNING(0.8f),
    EXCEEDED(1.0f)
}

data class BudgetAlert(
    val eventId: String,
    val eventName: String,
    val threshold: BudgetThreshold,
    val spent: Double,
    val budget: Double
) {
    /** Negative once the budget is blown, which the alert says rather than hides. */
    val remaining: Double get() = budget - spent
    val percentUsed: Int get() = if (budget > 0) ((spent / budget) * 100).toInt() else 0
}

/**
 * What to post now, and the crossing state to remember.
 *
 * [crossed] is the whole truth about which thresholds are currently breached, not a
 * delta — the caller stores it verbatim. That is what makes the alert re-fire after a
 * correction: delete the expense that pushed an event over, the key drops out of
 * [crossed], and crossing 100% again months later is news again.
 */
data class AlertEvaluation(
    val toPost: List<BudgetAlert>,
    val crossed: Set<String>
)

/**
 * Decides which budget alerts are due, with no Android or Room in sight so the rules run
 * as a plain JVM test.
 *
 * The whole design rests on one idea: an alert fires on the *transition* into a
 * threshold, never on the state of being past it. Firing on state would mean an alert
 * every time the app recomputed analytics — every expense added, every app launch — for
 * an event that has been over budget since March.
 */
object BudgetAlerts {

    /** Stable key for one event's crossing of one threshold. */
    fun key(eventId: String, threshold: BudgetThreshold): String = "$eventId@${threshold.name}"

    fun evaluate(
        events: List<EventAnalytics>,
        alreadyNotified: Set<String>
    ): AlertEvaluation {
        val crossed = mutableSetOf<String>()
        val toPost = mutableListOf<BudgetAlert>()

        for (event in events) {
            // A zero budget is a placeholder somebody has not filled in yet, and every
            // fraction of it is infinite. Nothing to alert on.
            if (event.budget <= 0) continue

            val breached = BudgetThreshold.entries.filter { event.percentUsed >= it.fraction }
            val keys = breached.map { key(event.eventId, it) }
            crossed += keys

            // Spend can jump both thresholds in one expense. The user is told once, at
            // the level they actually reached — being warned they are at 80% in the same
            // breath as being told they are over is noise. Both are recorded as notified
            // so the warning does not arrive late, on the next expense.
            val newest = breached
                .filter { key(event.eventId, it) !in alreadyNotified }
                .maxByOrNull { it.fraction }
                ?: continue

            toPost += BudgetAlert(
                eventId = event.eventId,
                eventName = event.eventName,
                threshold = newest,
                spent = event.totalSpent,
                budget = event.budget
            )
        }

        // Only keys for events still in the list survive, so a deleted event cannot leave
        // its crossings behind to grow the stored set forever.
        return AlertEvaluation(toPost = toPost, crossed = crossed)
    }
}
