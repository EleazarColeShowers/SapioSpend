package com.el.sapiospend

import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.util.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyTest {

    @Test
    fun `amounts are grouped and carry the currency symbol`() {
        assertEquals("₦1,250,000", 1_250_000.0.formatMoney(AppCurrency.NGN))
        assertEquals("$1,250,000", 1_250_000.0.formatMoney(AppCurrency.USD))
        assertEquals("£1,250,000", 1_250_000.0.formatMoney(AppCurrency.GBP))
        assertEquals("KSh1,250,000", 1_250_000.0.formatMoney(AppCurrency.KES))
    }

    /** Kobo are shown when present and suppressed when the figure is whole. */
    @Test
    fun `fractions are kept only when they exist`() {
        assertEquals("₦1,500", 1500.0.formatMoney(AppCurrency.NGN))
        assertEquals("₦1,500.5", 1500.50.formatMoney(AppCurrency.NGN))
        assertEquals("₦1,500.55", 1500.55.formatMoney(AppCurrency.NGN))
    }

    @Test
    fun `zero and negative amounts format`() {
        assertEquals("₦0", 0.0.formatMoney(AppCurrency.NGN))
        assertEquals("₦-2,000", (-2000.0).formatMoney(AppCurrency.NGN))
    }

    /**
     * The stored value is the code, so the picker can be reordered or an entry retired
     * without silently re-denominating somebody's saved budget.
     */
    @Test
    fun `currency is resolved by code`() {
        assertEquals(AppCurrency.USD, AppCurrency.fromCode("USD"))
        assertEquals(AppCurrency.GHS, AppCurrency.fromCode("GHS"))
    }

    @Test
    fun `an unknown or missing code falls back to the default`() {
        assertEquals(AppCurrency.DEFAULT, AppCurrency.fromCode(null))
        assertEquals(AppCurrency.DEFAULT, AppCurrency.fromCode(""))
        assertEquals(AppCurrency.DEFAULT, AppCurrency.fromCode("XYZ"))
        // A pre-currency install has nothing stored and must keep reading as naira.
        assertEquals(AppCurrency.NGN, AppCurrency.DEFAULT)
    }

    @Test
    fun `every currency has a distinct code and a symbol`() {
        val codes = AppCurrency.entries.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        AppCurrency.entries.forEach {
            assertEquals("${it.name} needs a symbol", true, it.symbol.isNotBlank())
            assertEquals("${it.name} needs a display name", true, it.displayName.isNotBlank())
        }
    }
}
