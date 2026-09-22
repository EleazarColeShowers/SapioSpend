package com.el.sapiospend

import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.FxRates
import com.el.sapiospend.util.formatAmountInput
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.util.inDisplayCurrency
import com.el.sapiospend.util.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversion boundary.
 *
 * Every test here pins [from]/[to] and the rate table explicitly rather than leaning on
 * the process-wide globals, so a test cannot pass because it happened to run after one
 * that left the active currency somewhere convenient.
 */
class FxRatesTest {

    /** Round figures, so an expected value can be read off the test rather than computed. */
    private val rates = FxRates(
        perUsd = mapOf("USD" to 1.0, "NGN" to 1600.0, "GBP" to 0.80, "EUR" to 1.0),
        asOf = 1_777_593_600_000L,
        source = FxRates.Source.FETCHED
    )

    @Test
    fun `converting through the pivot`() {
        assertEquals(1600.0, rates.convert(1.0, AppCurrency.USD, AppCurrency.NGN), 1e-9)
        assertEquals(1.0, rates.convert(1600.0, AppCurrency.NGN, AppCurrency.USD), 1e-9)
        // Neither side is the pivot: GBP -> USD -> NGN.
        assertEquals(2000.0, rates.convert(1.0, AppCurrency.GBP, AppCurrency.NGN), 1e-9)
    }

    /** The common case, and the one that must be free of rounding: no rate is consulted. */
    @Test
    fun `converting a currency to itself is exact`() {
        val awkward = 1234.567891234
        assertEquals(awkward, rates.convert(awkward, AppCurrency.NGN, AppCurrency.NGN), 0.0)
        // True even of a table that could not price it at all.
        val empty = FxRates(emptyMap(), 0L, FxRates.Source.FETCHED)
        assertEquals(awkward, empty.convert(awkward, AppCurrency.ZAR, AppCurrency.ZAR), 0.0)
    }

    @Test
    fun `a round trip through another currency comes back`() {
        val original = 2_500_000.0
        val inPounds = rates.convert(original, AppCurrency.NGN, AppCurrency.GBP)
        assertEquals(original, rates.convert(inPounds, AppCurrency.GBP, AppCurrency.NGN), 1e-6)
    }

    /**
     * A currency the table cannot price falls back to the amount as-is rather than to a
     * zero. It is the app's old relabel-only behaviour: wrong, but visibly wrong, and it
     * does not wipe a figure off a screen full of somebody's budget.
     */
    @Test
    fun `a missing or nonsense rate leaves the amount alone`() {
        val broken = FxRates(
            perUsd = mapOf("USD" to 1.0, "NGN" to 0.0, "GBP" to Double.NaN),
            asOf = 0L,
            source = FxRates.Source.FETCHED
        )
        assertEquals(500.0, broken.convert(500.0, AppCurrency.USD, AppCurrency.NGN), 0.0)
        assertEquals(500.0, broken.convert(500.0, AppCurrency.USD, AppCurrency.GBP), 0.0)
        assertEquals(500.0, broken.convert(500.0, AppCurrency.USD, AppCurrency.KES), 0.0)
    }

    /**
     * The bundled table is the floor the whole feature stands on: it is what a phone that
     * has never been online converts with. A currency added to the picker without a rate
     * here would silently relabel instead of converting, which is the bug this feature
     * exists to fix.
     */
    @Test
    fun `bundled rates cover every currency the picker offers`() {
        assertTrue(FxRates.BUNDLED.isComplete)
        AppCurrency.entries.forEach {
            assertNotNull("${it.code} has no bundled rate", FxRates.BUNDLED.rateOf(it))
        }
    }

    @Test
    fun `merging keeps coverage the newer table lost`() {
        val partial = FxRates(mapOf("NGN" to 1700.0), 2_000_000_000_000L, FxRates.Source.FETCHED)
        val merged = FxRates.BUNDLED.mergedWith(partial)

        assertEquals(1700.0, merged.rateOf(AppCurrency.NGN)!!, 1e-9)
        assertEquals(FxRates.BUNDLED.rateOf(AppCurrency.ZAR), merged.rateOf(AppCurrency.ZAR))
        assertEquals(partial.asOf, merged.asOf)
        assertEquals(FxRates.Source.FETCHED, merged.source)
        assertTrue(merged.isComplete)
    }

    @Test
    fun `merging with nothing changes nothing`() {
        assertEquals(FxRates.BUNDLED, FxRates.BUNDLED.mergedWith(null))
    }

    @Test
    fun `a table survives being written and read back`() {
        val restored = FxRates.deserialize(rates.serialize())
        assertEquals(rates, restored)
    }

    @Test
    fun `nothing usable deserialises to null`() {
        assertNull(FxRates.deserialize(null))
        assertNull(FxRates.deserialize(""))
        assertNull(FxRates.deserialize("not a rate table"))
        // Right shape, unusable contents: every rate is junk, so there is nothing to keep.
        assertNull(FxRates.deserialize("123|FETCHED|NGN:abc,USD:-4"))
    }

    @Test
    fun `age is measured in whole days and never goes negative`() {
        val day = 86_400_000L
        val table = FxRates(mapOf("USD" to 1.0), asOf = 10 * day, source = FxRates.Source.FETCHED)
        assertEquals(0L, table.ageInDays(now = 10 * day))
        assertEquals(3L, table.ageInDays(now = 13 * day + day / 2))
        // A phone whose clock is behind the rates is not a table from the future.
        assertEquals(0L, table.ageInDays(now = 5 * day))
        assertTrue(table.isStale(now = 30 * day))
    }

    // --- The boundary functions ------------------------------------------------------

    @Test
    fun `an amount is converted before it is formatted`() {
        assertEquals(
            "$1,000",
            1_600_000.0.formatMoney(AppCurrency.USD, from = AppCurrency.NGN, rates = rates)
        )
    }

    /** A typed figure is read in the display currency and stored in the base one. */
    @Test
    fun `typed amounts are parsed into the base currency`() {
        assertEquals(
            1_600_000.0,
            "1000".parseAmount(AppCurrency.USD, to = AppCurrency.NGN, rates = rates)!!,
            1e-9
        )
    }

    /**
     * Blank has to stay distinguishable from zero: an empty budget field is "not filled
     * in yet" and a zero one is a save that should be refused.
     */
    @Test
    fun `an unparseable amount is null rather than zero`() {
        assertNull("".parseAmount(AppCurrency.USD, to = AppCurrency.NGN, rates = rates))
        assertNull("abc".parseAmount(AppCurrency.USD, to = AppCurrency.NGN, rates = rates))
        assertEquals(0.0, "0".parseAmount(AppCurrency.USD, to = AppCurrency.NGN, rates = rates)!!, 0.0)
    }

    /**
     * The round trip that matters most: opening an expense for editing and saving it
     * again without touching the amount must not move the figure.
     */
    @Test
    fun `editing an amount in the currency it was recorded in does not move it`() {
        val stored = 1_234_567.89
        val shown = stored.formatAmountInput(AppCurrency.NGN, from = AppCurrency.NGN, rates = rates)
        assertEquals(
            stored,
            shown.parseAmount(AppCurrency.NGN, to = AppCurrency.NGN, rates = rates)!!,
            0.0
        )
    }

    /** A converted figure is rounded to something a person can actually edit. */
    @Test
    fun `a converted amount is prefilled at two decimals`() {
        // 50,000 naira at 1,600 to the dollar is $31.25 exactly; 50,001 is not.
        assertEquals("31.25", 50_000.0.formatAmountInput(AppCurrency.USD, AppCurrency.NGN, rates))
        assertEquals("31.25", 50_001.0.formatAmountInput(AppCurrency.USD, AppCurrency.NGN, rates))
        // Whole numbers still lose the trailing ".0" the text field cannot use.
        assertEquals("1000", 1_600_000.0.formatAmountInput(AppCurrency.USD, AppCurrency.NGN, rates))
    }

    /** What the spreadsheet exports write into their cells. */
    @Test
    fun `bare export numbers are converted too`() {
        assertEquals(
            1000.0,
            1_600_000.0.inDisplayCurrency(AppCurrency.USD, from = AppCurrency.NGN, rates = rates),
            1e-9
        )
    }
}
