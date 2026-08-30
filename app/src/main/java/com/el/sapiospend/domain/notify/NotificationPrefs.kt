package com.el.sapiospend.domain.notify

/**
 * How often the app nudges the user to log what they have spent.
 *
 * Off is the default. A reminder about an event that is running out of days is
 * information the user asked for by setting a period; a recurring "come and use the
 * app" nudge is not, and one that arrives unasked is the reason people turn every
 * notification from an app off at once.
 */
enum class CheckInCadence(val label: String) {
    OFF("Off"),
    DAILY("Every day"),
    WEEKLY("Every Monday")
}

/**
 * What the user has agreed to be told about, and when.
 *
 * Held here rather than in [com.el.sapiospend.settings.SettingsRepository] so the rules
 * that read it — which reminders are due, whether a check-in fires today — stay pure
 * Kotlin and unit-testable without Android. The repository owns persistence only.
 *
 * [hourOfDay] applies to every scheduled notification, because the alarm behind them is
 * a single daily tick: one wake-up that decides what to say is cheaper on the battery
 * than an alarm per event, and it cannot leave orphaned alarms behind when an event is
 * deleted.
 */
data class NotificationPrefs(
    val eventReminders: Boolean = true,
    /** Days before an event's end date to warn. 0 means only on the closing day. */
    val reminderLeadDays: Int = DEFAULT_LEAD_DAYS,
    val budgetAlerts: Boolean = true,
    val checkIn: CheckInCadence = CheckInCadence.OFF,
    val hourOfDay: Int = DEFAULT_HOUR
) {
    /** Whether the user has asked to be told anything at all. */
    val anyEnabled: Boolean get() = eventReminders || budgetAlerts || checkIn != CheckInCadence.OFF

    /**
     * Whether the daily alarm is worth setting at all. Budget alerts are excluded: they
     * are evaluated live as expenses are written, and the daily sweep only exists to
     * catch a threshold crossed while the app was closed — which cannot happen, since
     * nothing but the app writes expenses. Reminders and check-ins are the only reasons
     * to wake up.
     */
    val needsDailyTick: Boolean get() = eventReminders || checkIn != CheckInCadence.OFF

    companion object {
        const val DEFAULT_LEAD_DAYS = 1
        const val DEFAULT_HOUR = 9

        /** The lead times offered in Settings — a picker, not a free-text field. */
        val LEAD_DAY_OPTIONS = listOf(0, 1, 3, 7)

        /** Hours offered in Settings. Kept to waking hours; nobody wants a 3am budget alert. */
        val HOUR_OPTIONS = listOf(7, 9, 12, 18, 21)
    }
}
