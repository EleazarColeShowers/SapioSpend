package com.el.sapiospend.util

import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.settings.AppCurrency
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An amount with its currency symbol — "₦1,250,000".
 *
 * The currency defaults to whatever the user picked in Settings, read at the call site.
 * Because [ActiveCurrency] is Compose snapshot state, a composable calling this without
 * an argument subscribes to the setting and re-renders when it changes; the exporters,
 * which are not composables, simply read the current value on whatever thread they run
 * on. Pass [currency] explicitly to format in something other than the active choice —
 * which is mostly what tests want.
 */
fun Double.formatMoney(currency: AppCurrency = ActiveCurrency.value): String =
    "${currency.symbol}${amountFormat.get().format(this)}"

fun Long.formatDate(): String = dateFormat.get().format(Date(this))

/**
 * An amount as it should appear *inside a text field* — plain digits, no separators or
 * currency mark, and no trailing ".0" on a whole number. Whatever comes back out of the
 * field has to parse with toDoubleOrNull, so this deliberately does not use the grouped
 * formatting [formatMoney] applies for display.
 */
fun Double.formatAmountInput(): String =
    if (this == kotlin.math.floor(this) && !this.isInfinite()) "%.0f".format(this) else this.toString()

// --- Formatter caching ------------------------------------------------------------
// NumberFormat and SimpleDateFormat are expensive to build and neither is thread-safe,
// and these run for every visible amount and date on a scrolling list. One instance per
// thread rather than one per call.

/**
 * Grouping is pinned to US regardless of device locale, so an amount always reads as
 * 1,250,000 — a locale that groups with dots would render a naira figure as 1.250.000
 * and invite it being read as a decimal.
 */
private val amountFormat: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
    NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
}

/** Month names do follow the device locale, so this rebuilds if the locale changes. */
private val dateFormat = LocaleBound { SimpleDateFormat("MMM d, yyyy", it) }

/**
 * A per-thread cached formatter that rebuilds itself if the default locale changes.
 *
 * A cache that ignored a locale switch would keep formatting in the old one for the life
 * of the process, since the app is not restarted for every locale change.
 */
internal class LocaleBound<T : Any>(private val create: (Locale) -> T) {

    private val holder = ThreadLocal<Pair<Locale, T>>()

    fun get(): T {
        val locale = Locale.getDefault()
        holder.get()?.let { (cached, format) -> if (cached == locale) return format }
        return create(locale).also { holder.set(locale to it) }
    }
}
