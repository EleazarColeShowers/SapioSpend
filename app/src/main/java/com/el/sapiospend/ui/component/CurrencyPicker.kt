package com.el.sapiospend.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.R
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.FxRates
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatMoney

/**
 * Picks the currency one budget is kept in.
 *
 * Chips rather than a dropdown because the whole list is eight entries and a dropdown
 * would hide seven of them behind a tap — the point of this control is that somebody
 * planning in dollars sees immediately that they can.
 *
 * The rate line underneath is what makes the choice legible: a Nigerian planner picking
 * USD wants to know what that means in naira before they type a target into the field
 * above, and a quoted rate with the date it was taken is the only honest way to say it.
 * Absent when the budget's currency is already the one the app is being read in, where
 * it would only restate the figure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrencyPicker(
    selected: AppCurrency,
    display: AppCurrency,
    rates: FxRates,
    /** False once the budget has figures in it and switching would rewrite them. */
    enabled: Boolean = true,
    label: String? = null,
    onSelect: (AppCurrency) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label ?: stringResource(R.string.field_currency).uppercase(),
            color = AppColors.Secondary,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
        Text(
            stringResource(R.string.field_currency_hint),
            color = AppColors.Border,
            fontSize = 12.sp
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppCurrency.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    enabled = enabled || option == selected,
                    onClick = { onSelect(option) },
                    label = { Text("${option.symbol} ${option.code}", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Black,
                        selectedLabelColor = Color.White,
                        containerColor = AppColors.BG,
                        labelColor = AppColors.Secondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = enabled || option == selected,
                        selected = option == selected,
                        borderColor = AppColors.Border,
                        selectedBorderColor = AppColors.Black
                    )
                )
            }
        }

        if (selected != display) {
            Text(
                stringResource(
                    R.string.currency_rate_line,
                    selected.code,
                    // Formatted in the display currency and from it, so the quoted figure
                    // is the rate itself rather than the rate put through a conversion.
                    rates.unitRate(selected, display).formatMoney(display, from = display, rates = rates),
                    rates.asOf.formatDate()
                ),
                color = if (rates.isStale()) AppColors.Danger else AppColors.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
