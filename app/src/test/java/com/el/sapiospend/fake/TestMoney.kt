package com.el.sapiospend.fake

import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.BudgetMoney
import com.el.sapiospend.settings.FxRates

/**
 * A budget kept in the default currency, read in the same one.
 *
 * Every parse and format through it is exact — no rate is consulted — which is what a
 * test about plan arithmetic or category rows wants. A test that is actually about
 * conversion builds its own [BudgetMoney] with two different currencies.
 */
val TEST_MONEY = BudgetMoney(
    currency = AppCurrency.DEFAULT,
    display = AppCurrency.DEFAULT,
    rates = FxRates.BUNDLED
)
