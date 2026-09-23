package com.el.sapiospend.settings

/**
 * How one budget's amounts are rendered.
 *
 * A budget is recorded in [currency] — the currency its owner chose to plan in — and
 * read back in that same currency, so a $5,000 target is $5,000 next month whatever the
 * rate has done. That exactness is the point: the app-wide [ActiveCurrency] converts on
 * the way out, and converting a figure out and back is how a target the user typed
 * starts drifting by a few dollars every time the feed moves.
 *
 * [display] is what the *rest* of the app is being read in, and it is only ever used for
 * the secondary "≈" line beneath a figure — an orientation for someone who thinks in
 * naira looking at a dollar goal, never the number the budget is actually kept in.
 *
 * Figures from several budgets cannot be added up in their own currencies, so anything
 * that spans budgets — the Home overview, the Insights portfolio, the digest — converts
 * each budget into the base currency first and is formatted the app-wide way instead.
 */
data class BudgetMoney(
    /** What this budget's stored figures are, and what its screens show. */
    val currency: AppCurrency,
    /** What the rest of the app is read in, for the converted line. */
    val display: AppCurrency,
    val rates: FxRates
) {

    /** Exact — same currency in and out, so no rate is consulted. */
    val own: MoneyStyle get() = MoneyStyle(currency, currency, rates)

    /** This budget's figures converted into what the rest of the app reads in. */
    val inDisplay: MoneyStyle get() = MoneyStyle(display, currency, rates)

    /** Whether the converted line says anything the primary figure does not. */
    val converts: Boolean get() = currency != display

    /** How many [display] units one unit of this budget's currency buys. */
    val unitRate: Double get() = rates.unitRate(currency, display)

    val ratesAsOf: Long get() = rates.asOf

    val ratesAreStale: Boolean get() = rates.isStale()

    companion object {

        /**
         * The live answer for a budget stored with [currencyCode], read off the
         * process-wide currency settings.
         *
         * Those are Compose snapshot state, so a composable calling this subscribes to
         * the display currency and the rate table and redraws when either changes.
         *
         * A null code is a budget saved before budgets had a currency of their own,
         * which means it is in the app's base currency — see [ActiveBase].
         */
        fun forCode(currencyCode: String?): BudgetMoney = BudgetMoney(
            currency = AppCurrency.fromCode(currencyCode, ActiveBase.value),
            display = ActiveCurrency.value,
            rates = ActiveRates.value
        )
    }
}
