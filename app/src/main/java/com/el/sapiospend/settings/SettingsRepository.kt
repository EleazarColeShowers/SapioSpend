package com.el.sapiospend.settings

import android.content.Context
import android.content.SharedPreferences
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

    companion object {
        private const val PREFS_NAME = "sapio_settings"
        private const val KEY_CURRENCY = "currency"

        fun create(context: Context): SettingsRepository =
            SettingsRepository(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
