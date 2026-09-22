package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.R
import com.el.sapiospend.domain.notify.CheckInCadence
import com.el.sapiospend.domain.notify.NotificationPrefs
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.FxRates
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatMoney
import kotlinx.coroutines.launch

/** A figure with enough digits to show what the grouping and symbol actually look like. */
private const val PREVIEW_AMOUNT = 1_250_000.0

@Composable
fun SettingsScreen(
    currency: AppCurrency,
    onCurrencyChange: (AppCurrency) -> Unit,
    /** The currency the stored figures are in — what the preview converts *from*. */
    baseCurrency: AppCurrency = currency,
    rates: FxRates = FxRates.BUNDLED,
    /** Returns whether anything newer actually arrived. */
    onRefreshRates: suspend () -> Boolean = { false },
    notifications: NotificationPrefs,
    onNotificationsChange: (NotificationPrefs) -> Unit,
    /** False when the user has never been asked, or has said no. */
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {}
) {
    /**
     * Switching a notification on is the moment to ask for permission — the user has just
     * said what they want it for, which is the only context in which the system dialog
     * makes sense. Asked once per switch-on and never nagged: a denial is an answer.
     */
    fun update(value: NotificationPrefs) {
        onNotificationsChange(value)
        if (value.anyEnabled && !notificationsAllowed) onRequestNotificationPermission()
    }

    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    /** Null while nothing has been tried this visit; cleared a few seconds after it is shown. */
    var refreshResult by remember { mutableStateOf<String?>(null) }

    // The result line is a confirmation, not a status: leaving "Rates updated" on screen
    // for the rest of the session would have it still claiming a refresh just happened
    // ten minutes later.
    LaunchedEffect(refreshResult) {
        if (refreshResult != null) {
            kotlinx.coroutines.delay(4000)
            refreshResult = null
        }
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val updated = onRefreshRates()
            refreshing = false
            refreshResult = if (updated) "Rates updated." else "Couldn't reach the rate service — still using the rates below."
        }
    }

    val converting = currency != baseCurrency

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.BG)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
        ) {
            item {
                // No back arrow: this is a tab, and the bar below is the way out of it.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nav_settings),
                        color = AppColors.OnSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "CURRENCY",
                        color = AppColors.Secondary,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    // Both halves said plainly, because either one alone is the
                    // misunderstanding: that nothing converts, or that the figures
                    // themselves were rewritten and the originals are gone.
                    Text(
                        "Amounts are converted into the currency you pick. What you recorded is " +
                            "kept in ${baseCurrency.displayName} and never changed — switch back and " +
                            "your figures are exactly as you typed them.",
                        color = AppColors.Secondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Preview",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        // Shown as the sum it is rather than as a lone figure: a
                        // converted amount with nothing to convert *from* beside it is
                        // the thing nobody can sanity-check.
                        if (converting) {
                            Text(
                                PREVIEW_AMOUNT.formatMoney(baseCurrency, from = baseCurrency, rates = rates),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            PREVIEW_AMOUNT.formatMoney(currency, from = baseCurrency, rates = rates),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
            }

            item {
                RateCard(
                    base = baseCurrency,
                    display = currency,
                    rates = rates,
                    converting = converting,
                    refreshing = refreshing,
                    result = refreshResult,
                    onRefresh = ::refresh
                )
            }

            items(AppCurrency.entries, key = { it.code }) { option ->
                val selected = option == currency
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCurrencyChange(option) },
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The symbol is the thing the user is actually choosing, so it
                        // gets the visual weight rather than the three-letter code.
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(AppColors.BG, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option.symbol,
                                color = AppColors.OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                option.displayName,
                                color = AppColors.OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // The rate this row would convert at, so the choice is made
                            // with the number in front of you rather than after tapping
                            // it and reading your budget back differently.
                            Text(
                                if (option == baseCurrency) "${option.code} · what your figures are recorded in"
                                else "${option.code} · ${rates.unitRate(option, baseCurrency).formatMoney(baseCurrency, from = baseCurrency, rates = rates)} per ${option.symbol}1",
                                color = AppColors.Secondary,
                                fontSize = 12.sp
                            )
                        }

                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = AppColors.Success,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(
                    "NOTIFICATIONS",
                    "Nothing leaves your phone — every alert is worked out on the device from your own figures."
                )
            }

            // Shown rather than hidden behind the toggles: a user who denied the system
            // dialog months ago has no way to connect "my reminders stopped" to a
            // permission screen they no longer remember. The toggles stay usable, so the
            // choice is recorded and takes effect the moment permission is granted.
            if (!notificationsAllowed && notifications.anyEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Warning.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Text(
                            "Notifications are turned off for Sapio Spend. Turn them on in your phone's " +
                                "settings for these to arrive.",
                            color = AppColors.OnSurface,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            item {
                ToggleRow(
                    title = stringResource(R.string.settings_alerts_title),
                    subtitle = stringResource(R.string.settings_alerts_subtitle),
                    checked = notifications.budgetAlerts,
                    onCheckedChange = { update(notifications.copy(budgetAlerts = it)) }
                )
            }

            item {
                ToggleRow(
                    title = stringResource(R.string.settings_reminders_title),
                    subtitle = stringResource(R.string.settings_reminders_subtitle),
                    checked = notifications.eventReminders,
                    onCheckedChange = { update(notifications.copy(eventReminders = it)) }
                )
            }

            // Only when reminders are on: a lead time with nothing to lead is a control
            // that appears to do something and does not.
            if (notifications.eventReminders) {
                item {
                    ChoiceRow(
                        label = "Remind me",
                        options = NotificationPrefs.LEAD_DAY_OPTIONS,
                        selected = notifications.reminderLeadDays,
                        labelOf = ::leadDaysLabel,
                        onSelect = { update(notifications.copy(reminderLeadDays = it)) }
                    )
                }
            }

            item {
                ChoiceRow(
                    label = "Spending check-in",
                    options = CheckInCadence.entries,
                    selected = notifications.checkIn,
                    labelOf = { it.label },
                    onSelect = { update(notifications.copy(checkIn = it)) }
                )
            }

            // The hour only governs the scheduled notifications. Budget alerts fire the
            // moment an expense crosses the line, which is the whole point of them, so
            // offering to defer one to 9am would be a lie.
            if (notifications.needsDailyTick) {
                item {
                    ChoiceRow(
                        label = "Send these at",
                        options = NotificationPrefs.HOUR_OPTIONS,
                        selected = notifications.hourOfDay,
                        labelOf = ::hourLabel,
                        onSelect = { update(notifications.copy(hourOfDay = it)) }
                    )
                }
            }
        }
    }
}

/**
 * Where the rates came from, how old they are, and a way to ask for newer ones.
 *
 * Shown whether or not anything is being converted, because "you are reading in the
 * currency you recorded in, so no rate is involved" is itself the answer to the question
 * this card exists to answer, and hiding the card would leave it unanswered.
 *
 * The refresh is the only place in the app that touches the network, and it is a button
 * rather than something that happens silently while the screen is open: a user who is on
 * a metered connection or who would rather the app stayed off the internet should not
 * have to guess whether opening Settings costs them anything.
 */
@Composable
private fun RateCard(
    base: AppCurrency,
    display: AppCurrency,
    rates: FxRates,
    converting: Boolean,
    refreshing: Boolean,
    result: String?,
    onRefresh: () -> Unit
) {
    val stale = rates.isStale()
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        if (converting) {
                            "1 ${display.code} = ${rates.unitRate(display, base).formatMoney(base, from = base, rates = rates)}"
                        } else {
                            "No conversion — you're reading in the currency you record in"
                        },
                        color = AppColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        buildString {
                            append("Rates from ${rates.asOf.formatDate()}")
                            // Named rather than implied: "built in" tells a user with no
                            // signal that the app is working as designed, not failing.
                            if (rates.source == FxRates.Source.BUNDLED) append(" · built in")
                        },
                        color = if (stale) AppColors.Warning else AppColors.Secondary,
                        fontSize = 12.sp
                    )
                }

                // Disabled while in flight rather than hidden, so the row does not
                // reflow under the thumb that just tapped it.
                IconButton(onClick = onRefresh, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.Secondary
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Update exchange rates",
                            tint = AppColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Only worth saying once the figures could actually be off by something a
            // person would notice; before that it is a warning about nothing.
            if (stale && converting) {
                Text(
                    "These rates are ${rates.ageInDays()} days old. Converted amounts may be out by a " +
                        "few percent — tap refresh when you're online.",
                    color = AppColors.Warning,
                    fontSize = 12.sp
                )
            }

            result?.let {
                Text(it, color = AppColors.Secondary, fontSize = 12.sp)
            }

            Text(
                "Rates are stored on your phone and used offline. Refreshing is the only time " +
                    "Sapio Spend goes online, and it only asks for a public list of rates — none " +
                    "of your budget figures ever leave the device.",
                color = AppColors.Secondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = AppColors.Secondary, fontSize = 11.sp, letterSpacing = 0.5.sp)
        Text(subtitle, color = AppColors.Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp),
        // The whole row toggles, not just the switch: a 32dp target at the far edge of
        // the screen is the hardest thing on this page to hit.
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    color = AppColors.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(subtitle, color = AppColors.Secondary, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.Black
                )
            )
        }
    }
}

/** A short list of mutually exclusive choices — the shape every notification timing takes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                label,
                color = AppColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            // Wraps rather than scrolls: an option that has run off the right edge of a
            // row is an option nobody knows is there.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(labelOf(option), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Black,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

private fun leadDaysLabel(days: Int): String = when (days) {
    0 -> "On the day"
    1 -> "1 day before"
    else -> "$days days before"
}

/**
 * 12-hour labels because the options are a handful of everyday times and "9 AM" reads
 * faster than "09:00" to the audience this app is written for.
 */
private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
