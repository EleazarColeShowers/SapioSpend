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

    /** The currency amounts are *shown* in. */
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    private val _baseCurrency = MutableStateFlow(readBaseCurrency())

    /**
     * The currency amounts are *stored* in — what the figures in the database mean.
     *
     * Kept apart from [currency] so that switching what you are reading in cannot touch
     * what you recorded. Everything the user typed stays exactly as they typed it, in the
     * currency they typed it in, and the display currency is applied on the way out.
     *
     * It only moves in the one case where moving it is free: see [setCurrency].
     */
    val baseCurrency: StateFlow<AppCurrency> = _baseCurrency.asStateFlow()

    /**
     * Changes the currency the app displays in.
     *
     * Amounts are converted, not relabelled: a ₦50,000 budget read in USD is about $32,
     * worked out from [rates]. The stored figure does not change — switching back to
     * naira shows ₦50,000 again, to the kobo, because nothing was ever rewritten.
     *
     * [hasData] is the one lever on the base currency, and it exists because a fresh
     * install has no reason to record in naira. With nothing saved yet there is nothing
     * to convert, so adopting the new currency as the base is exact and free, and it
     * spares a user who has never seen a naira an app that stores their rent in one and
     * re-converts it at a different rate every time the feed moves. The moment there is
     * data, the base is frozen: re-denominating somebody's history behind a currency
     * picker is precisely the silent rewrite this design exists to avoid.
     */
    fun setCurrency(currency: AppCurrency, hasData: Boolean = true) {
        val editor = prefs.edit().putString(KEY_CURRENCY, currency.code)
        if (!hasData) editor.putString(KEY_BASE_CURRENCY, currency.code)
        editor.apply()
        _currency.value = currency
        if (!hasData) _baseCurrency.value = currency
    }

    private fun readCurrency(): AppCurrency =
        AppCurrency.fromCode(prefs.getString(KEY_CURRENCY, null))

    /**
     * An install from before this setting existed has figures in naira, because that is
     * all the app could record then — so a missing value must read as the default and
     * not as whatever the display currency happens to be now.
     */
    private fun readBaseCurrency(): AppCurrency =
        AppCurrency.fromCode(prefs.getString(KEY_BASE_CURRENCY, null))

    // --- Exchange rates ------------------------------------------------------------

    private val _rates = MutableStateFlow(readRates())

    /** The table conversions are done with. Never empty: it starts bundled. */
    val rates: StateFlow<FxRates> = _rates.asStateFlow()

    /**
     * The cached table laid over the bundled one, so a cache written by an older build
     * — or by a feed that has since dropped a currency — cannot leave a currency
     * unpriced. [FxRates.BUNDLED] is the floor, not the fallback.
     */
    private fun readRates(): FxRates =
        FxRates.BUNDLED.mergedWith(FxRates.deserialize(prefs.getString(KEY_RATES, null)))

    /**
     * Replaces the cached rates, if the new ones are actually newer.
     *
     * The date check is what stops a feed that is serving yesterday from walking the
     * table backwards after a successful refresh from a fresher source.
     */
    fun setRates(fetched: FxRates) {
        if (fetched.asOf < _rates.value.asOf) return
        val merged = FxRates.BUNDLED.mergedWith(fetched)
        prefs.edit().putString(KEY_RATES, merged.serialize()).apply()
        _rates.value = merged
    }

    /**
     * Pulls fresh rates if there is a network, and shrugs if there is not.
     *
     * Returns whether anything was updated, for a Settings screen that wants to say so.
     * Callers on the startup path ignore it: the app is fully usable on the rates it
     * already has, which is the entire point of shipping a bundled table.
     */
    suspend fun refreshRates(): Boolean =
        FxRateFetcher.fetch().map { setRates(it); true }.getOrDefault(false)

    /**
     * A background refresh on launch, skipped when the rates are already current.
     *
     * Rate feeds publish once a day, so asking again in the same day is a request that
     * cannot return anything new — it spends the user's battery and data to be told what
     * the app already knows. The check is against the rates' own publication date rather
     * than a "last attempted" stamp, which means an offline stretch keeps retrying (the
     * rates stay old, so a refresh stays due) instead of backing off exactly when it
     * matters most.
     */
    suspend fun refreshRatesIfDue(now: Long = System.currentTimeMillis()): Boolean =
        if (_rates.value.ageInDays(now) < 1L) false else refreshRates()

    /**
     * The three settings needed to render an amount, read together.
     *
     * For the widget and the notification tick, which run outside the app's process and
     * so cannot use the [ActiveCurrency]/[ActiveBase]/[ActiveRates] globals.
     */
    fun moneyStyle(): MoneyStyle = MoneyStyle(_currency.value, _baseCurrency.value, _rates.value)
    private val _notifications = MutableStateFlow(readNotifications())
    val notifications: StateFlow<NotificationPrefs> = _notifications.asStateFlow()
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

            checkIn = prefs.getString(KEY_CHECK_IN, null)
                ?.let { name -> CheckInCadence.entries.firstOrNull { it.name == name } }
                ?: defaults.checkIn,
            hourOfDay = prefs.getInt(KEY_HOUR, defaults.hourOfDay)
        )
    }

    companion object {
        private const val PREFS_NAME = "sapio_settings"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_BASE_CURRENCY = "base_currency"
        private const val KEY_RATES = "fx_rates"
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
