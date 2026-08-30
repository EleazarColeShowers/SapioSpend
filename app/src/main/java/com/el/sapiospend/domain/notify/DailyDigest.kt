package com.el.sapiospend.domain.notify

import com.el.sapiospend.domain.analytics.PortfolioAnalytics
import com.el.sapiospend.util.DateUtils
import java.util.Calendar

/** An event whose period is about to close, or closes today. */
data class EventReminder(
    val eventId: String,
    val eventName: String,
    /** Whole days left after today. 0 means the period ends today. */
    val daysRemaining: Int,
    val remaining: Double,
    val safeDailySpend: Double?
)

/** The state of every live budget, for the recurring nudge. */
data class CheckInSummary(
    val activeEvents: Int,
    val totalBudget: Double,
    val totalSpent: Double,
    val overBudgetCount: Int
) {
    val remaining: Double get() = totalBudget - totalSpent
}

/**
 * Everything one daily tick has to say, plus the alert state to store afterwards.
 */
data class Digest(
    val reminders: List<EventReminder> = emptyList(),
    val alerts: List<BudgetAlert> = emptyList(),
    val checkIn: CheckInSummary? = null,
    val alertState: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = reminders.isEmpty() && alerts.isEmpty() && checkIn == null
}

/**
 * What the app should say when the daily alarm fires.
 *
 * Pure, so the awkward cases — an event ending today, a check-in on the wrong weekday,
 * a portfolio with nothing in it — are unit tests rather than things you find out by
 * waiting until 9am.
 */
object DailyDigest {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun build(
        prefs: NotificationPrefs,
        portfolio: PortfolioAnalytics,
        alreadyNotified: Set<String>,
        now: Long = System.currentTimeMillis()
    ): Digest {
        val reminders =
            if (prefs.eventReminders) dueReminders(portfolio, prefs.reminderLeadDays, now) else emptyList()

        // The sweep is a backstop for a crossing that happened while nothing was
        // watching. Its real job is the state it returns: skipping the evaluation when
        // alerts are off would let stored crossings go stale, and turning alerts back on
        // would then replay every breach the user already knows about.
        val evaluation = BudgetAlerts.evaluate(portfolio.events, alreadyNotified)

        return Digest(
            reminders = reminders,
            alerts = if (prefs.budgetAlerts) evaluation.toPost else emptyList(),
            checkIn = checkInFor(prefs, portfolio, now),
            alertState = evaluation.crossed
        )
    }

    /**
     * Events at the lead time, and events closing today.
     *
     * Both, because they say different things: one is "you have three days left of a
     * budget you are 90% through", the other is "this is the last day it counts". A lead
     * time of 0 collapses the two into the single closing-day reminder.
     *
     * The countdown is measured in calendar days between midnights, not from
     * [com.el.sapiospend.domain.analytics.EventAnalytics.daysRemaining]. That figure is
     * built for pacing maths: it floors at zero, so a period that closed last March and
     * one closing this evening are both "0 days left" — and reminding somebody daily
     * about a budget that ended a year ago is exactly the behaviour that gets an app
     * muted. A calendar difference goes negative for a period that is past, which is the
     * distinction this needs.
     */
    private fun dueReminders(
        portfolio: PortfolioAnalytics,
        leadDays: Int,
        now: Long
    ): List<EventReminder> =
        portfolio.events.mapNotNull { event ->
            val end = event.periodEnd ?: return@mapNotNull null
            val daysUntilEnd = daysBetween(now, end)
            if (daysUntilEnd < 0) return@mapNotNull null
            if (daysUntilEnd != leadDays && daysUntilEnd != 0) return@mapNotNull null

            EventReminder(
                eventId = event.eventId,
                eventName = event.eventName,
                daysRemaining = daysUntilEnd,
                remaining = event.remaining,
                safeDailySpend = event.safeDailySpend
            )
        }

    /**
     * Whole calendar days from the day containing [from] to the day containing [to].
     *
     * Rounded rather than truncated, because two midnights either side of a daylight
     * saving change are 23 or 25 hours apart: dividing that down would report the day
     * before a period ends as two days before, and the reminder would arrive a day early
     * every spring.
     */
    private fun daysBetween(from: Long, to: Long): Int {
        val span = DateUtils.startOfDay(to) - DateUtils.startOfDay(from)
        return Math.round(span.toDouble() / MILLIS_PER_DAY).toInt()
    }

    /**
     * A check-in only when there is something to check in on. A weekly summary of an
     * empty app is the kind of notification that gets the app's notifications switched
     * off entirely.
     */
    private fun checkInFor(
        prefs: NotificationPrefs,
        portfolio: PortfolioAnalytics,
        now: Long
    ): CheckInSummary? {
        if (portfolio.eventCount == 0) return null
        val due = when (prefs.checkIn) {
            CheckInCadence.OFF -> false
            CheckInCadence.DAILY -> true
            CheckInCadence.WEEKLY -> dayOfWeek(now) == Calendar.MONDAY
        }
        if (!due) return null

        return CheckInSummary(
            activeEvents = portfolio.eventCount,
            totalBudget = portfolio.totalBudget,
            totalSpent = portfolio.totalSpent,
            overBudgetCount = portfolio.overBudgetCount
        )
    }

    /** Local time, matching every other date boundary in the app (see DateUtils). */
    private fun dayOfWeek(now: Long): Int =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
}
