package com.el.sapiospend.util

import com.el.sapiospend.settings.ActiveBase
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.settings.ActiveRates
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.FxRates
import com.el.sapiospend.settings.MoneyStyle
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- The currency boundary --------------------------------------------------------
//
// One rule holds everywhere in this app: **every Double amount is in the base currency**
// — the currency the figures were recorded in — and **every amount in a text field is
// in the display currency**. That means domain code, analytics, exports, alerts and the
// database never think about conversion at all; they are all working in the same unit.
//
// Exactly three functions cross that boundary, and they are all here: [formatMoney] and
// [formatAmountInput] on the way out, [parseAmount] on the way back in. A new amount
// field that reads with toDoubleOrNull instead of parseAmount stores a dollar figure as
// naira, so parse amounts through here and nowhere else.
//
// When display and base are the same currency — which is every user who never switches,
// and every user who switches back — all three are exact: the conversion short-circuits
// and no rate is consulted.

/**
 * An amount with its currency symbol — "₦1,250,000".
 *
 * Converts from the base currency to whatever the user is reading in, then formats. The
 * defaults are all Compose snapshot state, so a composable calling this with no arguments
 * subscribes to the currency choice *and* to the rate table: changing either in Settings
 * redraws every visible amount rather than leaving stale figures behind. The exporters
 * and the notifier are not composables and simply read the current values on whatever
 * thread they are on.
 *
 * Pass [from] and [currency] as the same currency to format without converting, which is
 * what a test checking grouping or symbols wants.
 */
fun Double.formatMoney(
    currency: AppCurrency = ActiveCurrency.value,
    from: AppCurrency = ActiveBase.value,
    rates: FxRates = ActiveRates.value
): String = "${currency.symbol}${amountFormat.get().format(rates.convert(this, from, currency))}"

/**
 * As [formatMoney], for a caller that had to read the currency settings off storage
 * rather than take them from the process-wide globals — see [MoneyStyle].
 */
fun Double.formatMoney(style: MoneyStyle): String =
    formatMoney(style.display, style.base, style.rates)

fun Long.formatDate(): String = dateFormat.get().format(Date(this))

/**
 * An amount as it should appear *inside a text field* — converted into the display
 * currency, then written as plain digits: no separators, no currency mark, and no
 * trailing ".0" on a whole number. Whatever comes back out of the field has to parse, so
 * this deliberately does not use the grouped formatting [formatMoney] applies.
 *
 * A converted figure is rounded to two decimals rather than shown at full precision,
 * because ₦50,000 at today's rate is $32.25806451612903 and nobody is editing that.
 * The rounding is only ever applied to a figure that was already converted — an amount
 * being re-edited in the currency it was recorded in comes back untouched, so opening an
 * expense and saving it without changing anything cannot move the number.
 */
fun Double.formatAmountInput(
    currency: AppCurrency = ActiveCurrency.value,
    from: AppCurrency = ActiveBase.value,
    rates: FxRates = ActiveRates.value
): String {
    val shown = if (currency == from) this else round2(rates.convert(this, from, currency))
    return if (shown == kotlin.math.floor(shown) && !shown.isInfinite()) "%.0f".format(shown)
    else shown.toString()
}

/**
 * What the user typed into an amount field, as a stored amount — read in the display
 * currency, returned in the base currency.
 *
 * Null for anything that is not a number, so a caller can tell an empty field from a
 * zero: a blank budget is "not filled in yet" and a zero budget is a save that should be
 * refused, and the two need to stay distinguishable.
 */
fun String.parseAmount(
    currency: AppCurrency = ActiveCurrency.value,
    to: AppCurrency = ActiveBase.value,
    rates: FxRates = ActiveRates.value
): Double? = toDoubleOrNull()?.let { rates.convert(it, currency, to) }

/**
 * A stored amount as a bare number in the display currency, with no symbol.
 *
 * For the CSV and the workbook, which put the currency code in a column of its own and
 * the figures in cells the planner can sum. They still have to be converted — a sheet
 * headed USD full of naira figures is worse than either honest option.
 */
fun Double.inDisplayCurrency(
    currency: AppCurrency = ActiveCurrency.value,
    from: AppCurrency = ActiveBase.value,
    rates: FxRates = ActiveRates.value
): Double = rates.convert(this, from, currency)

private fun round2(value: Double): Double =
    if (value.isFinite()) kotlin.math.round(value * 100.0) / 100.0 else value

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
