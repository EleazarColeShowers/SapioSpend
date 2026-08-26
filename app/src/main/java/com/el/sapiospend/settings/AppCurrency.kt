package com.el.sapiospend.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The currencies a budget can be denominated in.
 *
 * A curated list rather than every ISO 4217 code: a picker with 180 entries makes the
 * user scroll past 170 currencies nobody in this app's market will pick. These are the
 * naira plus the currencies a Nigerian planner or a diaspora user actually deals in —
 * add to the list rather than opening it up wholesale.
 *
 * [code] is what gets persisted, never the ordinal, so reordering this list or dropping
 * an entry cannot silently re-denominate somebody's saved budget.
 */
enum class AppCurrency(
    val code: String,
    val symbol: String,
    val displayName: String
) {
    NGN("NGN", "₦", "Nigerian Naira"),
    USD("USD", "$", "US Dollar"),
    GBP("GBP", "£", "British Pound"),
    EUR("EUR", "€", "Euro"),
    GHS("GHS", "₵", "Ghanaian Cedi"),
    KES("KES", "KSh", "Kenyan Shilling"),
    ZAR("ZAR", "R", "South African Rand"),
    CAD("CAD", "CA$", "Canadian Dollar");

    companion object {
        /** The app shipped naira-only, so an existing install must keep reading as naira. */
        val DEFAULT = NGN

        /** An unrecognised or missing code falls back to the default rather than throwing. */
        fun fromCode(code: String?): AppCurrency =
            entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/**
 * The currency every amount in the app is currently formatted in.
 *
 * This is deliberately process-wide rather than threaded through every composable. Money
 * is formatted in roughly sixty places spanning composables, the PDF writer and the
 * spreadsheet writer, and only the first of those can take a CompositionLocal — passing
 * a currency parameter down all three trees would be a large amount of plumbing for a
 * value that is genuinely global to the app.
 *
 * It is Compose snapshot state rather than a plain var, which is what makes that safe:
 * reading it inside composition subscribes, so changing the currency in Settings
 * recomposes every screen showing an amount instead of leaving stale symbols behind.
 * Reads from a background thread (the exporters) see the current global snapshot value,
 * which is exactly what they want.
 *
 * [SettingsRepository] owns persistence; this is only the live value. MainActivity seeds
 * it at startup and keeps it in sync.
 */
object ActiveCurrency {
    var value: AppCurrency by mutableStateOf(AppCurrency.DEFAULT)
}
