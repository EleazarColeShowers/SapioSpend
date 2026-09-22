package com.el.sapiospend.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A table of exchange rates, quoted against one pivot currency.
 *
 * Every rate is "how many units of this currency is 1 USD worth". USD is the pivot only
 * because it is what every free rate source publishes against — nothing in the app cares
 * that it is USD, and converting between two non-pivot currencies goes through it.
 *
 * Rates are a snapshot, never a live value: [asOf] is when this table was true, and it is
 * carried around with the numbers rather than kept beside them, because a rate without a
 * date is the thing that lets a two-year-old figure pass itself off as today's.
 */
data class FxRates(
    /** Currency code to units-per-1-USD. Always covers every [AppCurrency]. */
    val perUsd: Map<String, Double>,
    /** When these rates were published, epoch millis. */
    val asOf: Long,
    val source: Source
) {

    enum class Source {
        /** The table compiled into the APK. Works on a phone that has never been online. */
        BUNDLED,

        /** Pulled from a rate feed at some point and cached since. */
        FETCHED
    }

    /** Null when the table has no usable rate — a zero or a NaN is not one. */
    fun rateOf(currency: AppCurrency): Double? =
        perUsd[currency.code]?.takeIf { it.isFinite() && it > 0.0 }

    /**
     * [amount] of [from], expressed in [to].
     *
     * A missing rate degrades to returning the amount untouched, which is the app's old
     * relabel-only behaviour — wrong, but recognisably wrong, and far better than a zero
     * or a crash on a screen full of somebody's budget. [BUNDLED] covers every currency
     * the picker offers and [FxRateStore] never drops below it, so this is unreachable
     * short of a corrupt table.
     */
    fun convert(amount: Double, from: AppCurrency, to: AppCurrency): Double {
        if (from == to || amount == 0.0) return amount
        val fromRate = rateOf(from) ?: return amount
        val toRate = rateOf(to) ?: return amount
        return amount / fromRate * toRate
    }

    /** How many [to] one [from] buys — the figure worth showing the user. */
    fun unitRate(from: AppCurrency, to: AppCurrency): Double = convert(1.0, from, to)

    /** Whether this table can price every currency the picker offers. */
    val isComplete: Boolean
        get() = AppCurrency.entries.all { rateOf(it) != null }

    /** Whole days since these rates were published; negative clamped to zero. */
    fun ageInDays(now: Long = System.currentTimeMillis()): Long =
        ((now - asOf) / 86_400_000L).coerceAtLeast(0L)

    /**
     * Whether the user should be told these rates are old.
     *
     * A fortnight, not a day: this is a budgeting app, not a trading one, and a figure
     * that moved half a percent overnight does not need a warning banner. Two weeks is
     * roughly where a currency can have moved enough for a converted total to be
     * noticeably off, which is the point at which saying so is useful rather than noise.
     */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = ageInDays(now) >= STALE_AFTER_DAYS

    /**
     * This table with [other]'s rates laid over it, keeping this table's coverage.
     *
     * A fetched table that is missing a currency — a feed dropping a thin one, a half
     * written cache — leaves the bundled rate for that currency in place instead of
     * taking the whole app back to relabelling.
     */
    fun mergedWith(other: FxRates?): FxRates {
        if (other == null) return this
        val merged = perUsd.toMutableMap()
        other.perUsd.forEach { (code, rate) -> if (rate.isFinite() && rate > 0.0) merged[code] = rate }
        return FxRates(merged, other.asOf, other.source)
    }

    /**
     * The table as one line of text, for SharedPreferences.
     *
     * Hand-rolled rather than JSON because it is eight numbers and a date, and adding a
     * serialization library to the build to write that would be the tail wagging the dog.
     */
    fun serialize(): String = buildString {
        append(asOf).append('|').append(source.name).append('|')
        append(perUsd.entries.joinToString(",") { "${it.key}:${it.value}" })
    }

    companion object {

        /**
         * Rates compiled into the app, so a phone that has never had a connection still
         * converts rather than relabelling.
         *
         * These are approximate mid-market figures, not a quote — they are here to be
         * roughly right on first launch and to be replaced by [FxRateFetcher] the first
         * time the app sees a network. A currency added to [AppCurrency] must be added
         * here too; `bundled rates cover every currency` in the tests fails if it is not.
         */
        val BUNDLED = FxRates(
            perUsd = mapOf(
                "USD" to 1.0,
                "NGN" to 1327.88,
                "GBP" to 0.7449,
                "EUR" to 0.8693,
                "GHS" to 11.4479,
                "KES" to 129.577,
                "ZAR" to 16.3242,
                "CAD" to 1.3956
            ),
            // The date the figures above were taken. Bump it when you refresh them, and
            // never bump it without refreshing them — this date is what the app shows the
            // user and what it uses to decide the table has gone stale.
            asOf = BUNDLED_AS_OF,
            source = Source.BUNDLED
        )

        /** Null for anything this did not write, including a string from an older build. */
        fun deserialize(raw: String?): FxRates? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split('|')
            if (parts.size != 3) return null
            val asOf = parts[0].toLongOrNull() ?: return null
            val source = Source.entries.firstOrNull { it.name == parts[1] } ?: return null
            val rates = parts[2].split(',').mapNotNull { pair ->
                val (code, rate) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val value = rate.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                code to value
            }.toMap()
            return if (rates.isEmpty()) null else FxRates(rates, asOf, source)
        }
    }
}

/** How old a table has to be before the app says so on screen. */
const val STALE_AFTER_DAYS = 14L

/** 16 September 2026, 00:00 UTC. */
private const val BUNDLED_AS_OF = 1_789_516_800_000L

/**
 * The rate table the app is currently converting with.
 *
 * Compose snapshot state for the same reason [ActiveCurrency] is: money is formatted in
 * roughly sixty places, and a refreshed rate has to redraw all of them rather than wait
 * for each screen to be revisited. [SettingsRepository] owns persistence; this is the
 * live value, seeded and kept in step by MainActivity.
 */
object ActiveRates {
    var value: FxRates by mutableStateOf(FxRates.BUNDLED)
}

/**
 * The currency every amount in the database is denominated in.
 *
 * Distinct from [ActiveCurrency], and the distinction is the whole design: the base is
 * what the stored figures *are*, the active currency is what they are *shown as*. A
 * stored 50,000 with a base of NGN is fifty thousand naira no matter which currency the
 * user is currently reading in, so switching the display currency converts the view and
 * leaves the recorded data untouched.
 *
 * Existing installs recorded their figures before the app could convert anything, and
 * those figures are naira — which is why the default here has to stay [AppCurrency.NGN]
 * forever, whatever the picker's default becomes.
 */
/**
 * Everything needed to render a stored amount, in one value.
 *
 * The globals above are the right answer inside the app's own process, where MainActivity
 * keeps them current. The widget and the notification tick are *not* in that process — a
 * phone rebooted overnight wakes straight into the alarm, and the launcher draws the
 * widget without the app ever starting — so there they would still be at their defaults,
 * which for [ActiveBase] means quietly treating a dollar budget as naira. Those callers
 * read this off storage instead, and pass it explicitly.
 */
data class MoneyStyle(
    val display: AppCurrency,
    val base: AppCurrency,
    val rates: FxRates
)

object ActiveBase {
    var value: AppCurrency by mutableStateOf(AppCurrency.DEFAULT)
}
