package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.domain.notify.CheckInCadence
import com.el.sapiospend.domain.notify.NotificationPrefs
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.formatMoney

/** A figure with enough digits to show what the grouping and symbol actually look like. */
private const val PREVIEW_AMOUNT = 1_250_000.0

@Composable
fun SettingsScreen(
    currency: AppCurrency,
    onCurrencyChange: (AppCurrency) -> Unit,
    notifications: NotificationPrefs,
    onNotificationsChange: (NotificationPrefs) -> Unit,
    /** False when the user has never been asked, or has said no. */
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onBack: () -> Unit = {}
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.Secondary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Settings",
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
                    // Said plainly and up front, because the alternative reading —
                    // that switching currency converts the money — would have somebody
                    // believe their ₦2m wedding budget just became $2m.
                    Text(
                        "Changes how amounts are labelled. Your figures stay exactly as you entered them — nothing is converted.",
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
                        Text(
                            PREVIEW_AMOUNT.formatMoney(currency),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
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
                            Text(option.code, color = AppColors.Secondary, fontSize = 12.sp)
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
                    title = "Budget alerts",
                    subtitle = "When an event passes 80% of its budget, and again when it goes over",
                    checked = notifications.budgetAlerts,
                    onCheckedChange = { update(notifications.copy(budgetAlerts = it)) }
                )
            }

            item {
                ToggleRow(
                    title = "Event reminders",
                    subtitle = "Before a budget period ends, and on its closing day",
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
