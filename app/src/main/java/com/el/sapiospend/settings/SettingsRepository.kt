package com.el.sapiospend.settings

import android.content.Context
import android.content.SharedPreferences
import com.el.sapiospend.domain.notify.CheckInCadence
import com.el.sapiospend.domain.notify.NotificationPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences that are not budget data.
 *
 * Kept in SharedPreferences rather than Room: these are a handful of scalars with no
 * relationships and no history, and putting them in the database would mean a migration
 * every time a setting is added.
 *
 * Separate prefs file from [com.el.sapiospend.billing.LocalEntitlements] so clearing one
 * cannot take the other with it — losing a currency choice is an annoyance, losing a
 * purchase record is a support ticket.
 */
class SettingsRepository(private val prefs: SharedPreferences) {

    private val _currency = MutableStateFlow(readCurrency())
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    /**
     * Changes the currency the app displays in.
     *
     * Note that this re-labels existing amounts rather than converting them: a ₦50,000
     * budget switched to USD reads as $50,000, not $32. That is the honest behaviour for
     * an offline app — a conversion would need live FX rates, and a stale rate silently
     * rewriting somebody's recorded spend is far worse than a relabel they asked for.
     */
    fun setCurrency(currency: AppCurrency) {
        prefs.edit().putString(KEY_CURRENCY, currency.code).apply()
        _currency.value = currency
    }

    private fun readCurrency(): AppCurrency =
        AppCurrency.fromCode(prefs.getString(KEY_CURRENCY, null))

    // --- Notifications -------------------------------------------------------------
    // Exposed as one value rather than a flag per setting, because everything that reads
    // them — the scheduler, the digest, the alert publisher — needs the whole picture to
    // decide anything, and four separate flows would have them acting on half of it.

    private val _notifications = MutableStateFlow(readNotifications())
    val notifications: StateFlow<NotificationPrefs> = _notifications.asStateFlow()

    /**
     * Writes the whole preference block at once.
     *
     * Callers hand back a copy of the current value with one field changed, so a setting
     * this app has not shipped yet cannot be silently reset by an older screen.
     */
    fun setNotifications(value: NotificationPrefs) {
        prefs.edit()
            .putBoolean(KEY_EVENT_REMINDERS, value.eventReminders)
            .putInt(KEY_LEAD_DAYS, value.reminderLeadDays)
            .putBoolean(KEY_BUDGET_ALERTS, value.budgetAlerts)
            .putString(KEY_CHECK_IN, value.checkIn.name)
            .putInt(KEY_HOUR, value.hourOfDay)
            .apply()
        _notifications.value = value
    }

    private fun readNotifications(): NotificationPrefs {
        val defaults = NotificationPrefs()
        return NotificationPrefs(
            eventReminders = prefs.getBoolean(KEY_EVENT_REMINDERS, defaults.eventReminders),
            reminderLeadDays = prefs.getInt(KEY_LEAD_DAYS, defaults.reminderLeadDays),
            budgetAlerts = prefs.getBoolean(KEY_BUDGET_ALERTS, defaults.budgetAlerts),
            // Stored by name, never by ordinal, so reordering the enum cannot turn a
            // user's weekly nudge into a daily one.
            checkIn = prefs.getString(KEY_CHECK_IN, null)
                ?.let { name -> CheckInCadence.entries.firstOrNull { it.name == name } }
                ?: defaults.checkIn,
            hourOfDay = prefs.getInt(KEY_HOUR, defaults.hourOfDay)
        )
    }

    companion object {
        private const val PREFS_NAME = "sapio_settings"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_EVENT_REMINDERS = "notify_event_reminders"
        private const val KEY_LEAD_DAYS = "notify_lead_days"
        private const val KEY_BUDGET_ALERTS = "notify_budget_alerts"
        private const val KEY_CHECK_IN = "notify_check_in"
        private const val KEY_HOUR = "notify_hour"

        fun create(context: Context): SettingsRepository =
            SettingsRepository(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
