package com.el.sapiospend.notify

import android.content.Context
import android.content.SharedPreferences

/**
 * Which budget thresholds the user has already been told about.
 *
 * Not a user preference, so it lives apart from [com.el.sapiospend.settings.SettingsRepository]:
 * this is bookkeeping the app keeps on the user's behalf, and clearing it is harmless
 * (at worst one alert repeats) where clearing a currency choice is not.
 *
 * SharedPreferences rather than Room because it is a single set of short strings with no
 * relationships, and putting it in the database would mean a schema migration for
 * something that can be thrown away without losing any of the user's data.
 */
class BudgetAlertStore(private val prefs: SharedPreferences) {

    /**
     * getStringSet hands back an instance the framework may reuse; mutating it corrupts
     * the in-memory cache, and even reading it after a later commit is undefined. Copying
     * on the way out makes the returned set genuinely the caller's.
     */
    fun crossed(): Set<String> = prefs.getStringSet(KEY_CROSSED, null)?.toSet() ?: emptySet()

    /**
     * Stored as a whole set rather than added to, so a threshold that stops being
     * breached — an expense corrected, an event deleted — drops out and can alert again.
     */
    fun setCrossed(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_CROSSED, keys).apply()
    }

    companion object {
        private const val PREFS_NAME = "sapio_notify_state"
        private const val KEY_CROSSED = "crossed_thresholds"

        fun create(context: Context): BudgetAlertStore =
            BudgetAlertStore(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
