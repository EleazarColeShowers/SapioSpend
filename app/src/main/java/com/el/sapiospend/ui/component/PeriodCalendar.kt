package com.el.sapiospend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.DateUtils
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatPeriod
import java.util.Calendar

/**
 * The app's own date-range calendar, in place of Material's.
 *
 * Material's picker is a general-purpose date control: it carries a text-entry mode, a
 * year grid, its own typography and its own idea of a dialog, none of which match a
 * screen built out of flat cards and chips. This one does exactly one job — pick the two
 * ends of a budget period — in the surrounding visual language, and it works entirely in
 * local time so the day tapped is the day stored.
 *
 * Selection is deliberately two taps with no modes: the first sets the start and clears
 * whatever was there, the second closes the range. Tapping earlier than the current start
 * restarts from there rather than refusing, which is what people do when they mis-tap.
 */
@Composable
fun PeriodCalendar(
    selectedStart: Long?,
    selectedEnd: Long?,
    onSelect: (start: Long, end: Long?) -> Unit,
    modifier: Modifier = Modifier,
    today: Long = System.currentTimeMillis()
) {
    // Incoming bounds are a start-of-day and an end-of-day instant; the grid compares
    // days, so both are flattened to the start of their day first.
    val startDay = selectedStart?.let { DateUtils.startOfDay(it) }
    val endDay = selectedEnd?.let { DateUtils.startOfDay(it) }
    val todayDay = DateUtils.startOfDay(today)

    var visibleMonth by remember(startDay) {
        mutableStateOf(DateUtils.monthStart(startDay ?: today))
    }

    val weeks = remember(visibleMonth) { monthGrid(visibleMonth) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") {
                visibleMonth = DateUtils.addMonths(visibleMonth, -1)
            }
            Text(
                DateUtils.formatMonthYear(visibleMonth),
                color = AppColors.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            MonthArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") {
                visibleMonth = DateUtils.addMonths(visibleMonth, 1)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            DateUtils.weekdayInitials().forEach { initial ->
                Text(
                    initial,
                    color = AppColors.Secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // No spacing between the cells of a row: the in-range band is drawn as each
        // cell's background, and a gap would break it into dashes.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(CELL_HEIGHT))
                        } else {
                            DayCell(
                                day = day,
                                isStart = day == startDay,
                                isEnd = day == endDay,
                                isInRange = startDay != null && endDay != null && day > startDay && day < endDay,
                                isToday = day == todayDay,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // A complete range, or a tap before the start, begins
                                    // a new one; otherwise this closes the open range.
                                    if (startDay == null || endDay != null || day < startDay) {
                                        onSelect(day, null)
                                    } else {
                                        onSelect(startDay, day)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * [PeriodCalendar] in a dialog, holding the in-progress range so backing out leaves the
 * caller's dates untouched.
 */
@Composable
fun PeriodCalendarDialog(
    initialStart: Long?,
    initialEnd: Long?,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long) -> Unit
) {
    var draftStart by remember { mutableStateOf(initialStart) }
    var draftEnd by remember { mutableStateOf(initialEnd) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.Surface,
            tonalElevation = 0.dp
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Budget Period",
                        color = AppColors.OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        // The instruction changes as the range is built, so the dialog
                        // never leaves the user guessing which tap comes next.
                        when {
                            draftStart == null -> "Tap the day the budget starts"
                            draftEnd == null -> "Now tap the day it ends"
                            else -> formatPeriod(draftStart, draftEnd).orEmpty()
                        },
                        color = if (draftEnd != null) AppColors.OnSurface else AppColors.Secondary,
                        fontSize = 13.sp,
                        fontWeight = if (draftEnd != null) FontWeight.Medium else FontWeight.Normal
                    )
                }

                PeriodCalendar(
                    selectedStart = draftStart,
                    selectedEnd = draftEnd,
                    onSelect = { start, end ->
                        // The period convention the rest of the app relies on: the whole
                        // of the first day through the whole of the last.
                        draftStart = DateUtils.startOfDay(start)
                        draftEnd = end?.let { DateUtils.endOfDay(it) }
                    }
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val start = draftStart
                    val end = draftEnd
                    if (start != null && end != null) {
                        Text(
                            "${DateUtils.daysInPeriod(start, end)} days",
                            color = AppColors.Secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AppColors.Secondary, fontSize = 14.sp)
                    }
                    TextButton(
                        // Both ends required: half a range gives no period length, and so
                        // none of the pacing figures the period exists to produce.
                        enabled = start != null && end != null,
                        onClick = { if (start != null && end != null) onConfirm(start, end) }
                    ) {
                        Text(
                            "Done",
                            color = if (start != null && end != null) AppColors.Black else AppColors.Border,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Long,
    isStart: Boolean,
    isEnd: Boolean,
    isInRange: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isEndpoint = isStart || isEnd

    // The band is the cell's own background, shaped so a run of cells reads as one
    // continuous bar with rounded ends rather than seven separate pills.
    val bandShape: Shape = when {
        isStart && isEnd -> CircleShape
        isStart -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        isEnd -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
        else -> RectangleShape
    }
    val bandColor = if (isEndpoint || isInRange) AppColors.Black.copy(alpha = 0.07f) else Color.Transparent

    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .background(bandColor, bandShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isEndpoint) AppColors.Black else Color.Transparent)
                // Today keeps a ring when it is not an endpoint, so the current day stays
                // findable while scanning months.
                .then(
                    if (isToday && !isEndpoint) Modifier.border(1.dp, AppColors.Secondary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                dayOfMonthLabel(day),
                color = if (isEndpoint) Color.White else AppColors.OnSurface,
                fontSize = 13.sp,
                fontWeight = if (isEndpoint || isToday) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MonthArrow(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, tint = AppColors.Secondary)
    }
}

private val CELL_HEIGHT = 40.dp

/** The month laid out as weeks of seven, padded with nulls at both ends. */
private fun monthGrid(monthStart: Long): List<List<Long?>> {
    val leading = List<Long?>(DateUtils.leadingBlanks(monthStart)) { null }
    val days = (1..DateUtils.daysInMonth(monthStart)).map { DateUtils.dayOfMonth(monthStart, it) }
    val cells = leading + days
    val padded = cells + List<Long?>((7 - cells.size % 7) % 7) { null }
    return padded.chunked(7)
}

private fun dayOfMonthLabel(dayStart: Long): String =
    Calendar.getInstance()
        .apply { timeInMillis = dayStart }
        .get(Calendar.DAY_OF_MONTH)
        .toString()

/**
 * One day, picked on the same calendar the period picker uses.
 *
 * It drives [PeriodCalendar] with the selected day as both ends of a one-day range,
 * which is exactly how that component already draws a single circled day — so an expense
 * date and a budget period are chosen with the same grid, the same month arrows and the
 * same idea of what a tapped day means, rather than two calendars that merely resemble
 * each other.
 */
@Composable
fun DayCalendarDialog(
    initialDay: Long,
    title: String = "Date",
    onDismiss: () -> Unit,
    onConfirm: (day: Long) -> Unit
) {
    var draft by remember { mutableStateOf(DateUtils.startOfDay(initialDay)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = AppColors.Surface, tonalElevation = 0.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        color = AppColors.OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(draft.formatDate(), color = AppColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                PeriodCalendar(
                    selectedStart = draft,
                    selectedEnd = draft,
                    // Both ends are always set, so every tap starts a fresh "range" — and
                    // a one-day range is the day that was tapped.
                    onSelect = { day, _ -> draft = DateUtils.startOfDay(day) }
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = AppColors.Secondary, fontSize = 14.sp)
                    }
                    TextButton(onClick = { onConfirm(draft) }) {
                        Text("Done", color = AppColors.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
