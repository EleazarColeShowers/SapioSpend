package com.el.sapiospend.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.domain.analytics.CategoryBreakdown
import com.el.sapiospend.domain.analytics.MonthlySpend
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.theme.ChartColors
import com.el.sapiospend.util.formatMoney

/**
 * The app's charts, drawn on a Canvas rather than pulled in from a charting library.
 *
 * A library would arrive with its own colours, its own type and its own idea of what a
 * chart looks like, and every one of them would have to be overridden to match a screen
 * this plain — for three chart types that is more work than drawing them, and it is a
 * dependency shipped to every user for the sake of one screen.
 *
 * Shared rules, applied by every chart here: bars are thin and grow from one baseline,
 * the data-end is rounded and the baseline end is square, touching fills are separated
 * by a gap in the surface colour rather than by a stroke, and text never wears the data
 * colour — a swatch beside a label carries identity instead.
 */

private val BAR_CORNER = 4.dp
private val SURFACE_GAP = 2.dp

// ---------------------------------------------------------------------------------
// Planned vs actual
// ---------------------------------------------------------------------------------

/**
 * Planned against actual for each category, as paired horizontal bars.
 *
 * Both series share one scale — the largest figure anywhere in the chart — so a bar in
 * one category means the same thing as a bar in another. Scaling each row to its own
 * category would make a ₦5,000 overrun and a ₦5,000,000 one draw identically.
 *
 * With no plan behind it the chart quietly becomes a single-series breakdown of actual
 * spend: one bar per row and no legend, because with one series the heading already
 * says what is plotted.
 */
@Composable
fun PlannedVsActualChart(
    categories: List<CategoryBreakdown>,
    modifier: Modifier = Modifier,
    maxRows: Int = 8
) {
    val rows = remember(categories, maxRows) {
        categories.filter { it.actual > 0 || it.planned > 0 }
            .sortedByDescending { maxOf(it.actual, it.planned) }
            .take(maxRows)
    }
    if (rows.isEmpty()) return

    val hasPlan = rows.any { it.planned > 0 }
    val scale = rows.maxOf { maxOf(it.actual, it.planned) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (hasPlan) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendKey("Planned", ChartColors.Planned)
                LegendKey("Actual", ChartColors.Actual)
            }
        }

        rows.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category.category,
                        color = AppColors.OnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Every bar is labelled with its figure: three of the hues on these
                    // screens sit below 3:1 against the card, and an unlabelled bar of
                    // money is not something to make anyone squint at anyway.
                    Text(
                        if (category.planned > 0) {
                            "${category.actual.formatMoney()} / ${category.planned.formatMoney()}"
                        } else {
                            category.actual.formatMoney()
                        },
                        color = if (category.isOverPlan) AppColors.Danger else AppColors.Secondary,
                        fontSize = 12.sp
                    )
                }

                if (hasPlan) {
                    HorizontalBar(category.planned / scale, ChartColors.Planned)
                    Spacer(Modifier.height(SURFACE_GAP))
                }
                HorizontalBar(
                    fraction = category.actual / scale,
                    color = if (category.isOverPlan) ChartColors.Over else ChartColors.Actual
                )

                if (category.isUnplanned && hasPlan) {
                    Text("not in the plan", color = AppColors.Secondary, fontSize = 10.sp)
                }
            }
        }
    }
}

/** One bar of the pair: rounded at the data end, square where it meets the baseline. */
@Composable
private fun HorizontalBar(fraction: Double, color: Color, height: Dp = 8.dp) {
    val safeFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val radius = BAR_CORNER.toPx()
        drawRoundedEndRect(0f, 0f, size.width, size.height, ChartColors.Track, radius)
        // A category with a real figure never draws as nothing: below the corner radius
        // the bar would render as a sliver of rounding and read as zero.
        val barWidth = if (safeFraction > 0f) maxOf(size.width * safeFraction, radius * 2) else 0f
        if (barWidth > 0f) drawRoundedEndRect(0f, 0f, barWidth, size.height, color, radius)
    }
}

// ---------------------------------------------------------------------------------
// Spend over time
// ---------------------------------------------------------------------------------

/**
 * Monthly spend as columns, with the selected month's figure spelled out above them.
 *
 * A phone has no hover, so the tooltip becomes a tap: touching a column moves the
 * headline to that month. It opens on the most recent month, which is the one the
 * reader came for.
 */
@Composable
fun SpendTrendChart(
    points: List<MonthlySpend>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    if (points.isEmpty()) return

    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex) }
    val selected = points.getOrNull(selectedIndex) ?: points.last()
    val peak = points.maxOf { it.total }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                selected.total.formatMoney(),
                color = AppColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                if (selected.total > 0) "spent in ${selected.label}" else "nothing spent in ${selected.label}",
                color = AppColors.Secondary,
                fontSize = 12.sp
            )
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset: Offset ->
                        val slot = size.width / points.size
                        if (slot > 0) {
                            selectedIndex = (offset.x / slot).toInt().coerceIn(0, points.lastIndex)
                        }
                    }
                }
        ) {
            val slot = size.width / points.size
            val radius = BAR_CORNER.toPx()
            val baseline = size.height - 1.dp.toPx()
            // Columns are capped rather than filling their slot, so the leftover of each
            // slot stays as air instead of becoming ink.
            val barWidth = minOf(slot - SURFACE_GAP.toPx() * 2, 24.dp.toPx()).coerceAtLeast(2.dp.toPx())

            drawRect(
                color = ChartColors.Track,
                topLeft = Offset(0f, baseline),
                size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())
            )

            points.forEachIndexed { index, point ->
                val fraction = if (peak > 0) (point.total / peak).toFloat() else 0f
                // Even an empty month keeps a stub of a column, so the month is visibly
                // present at zero rather than missing from the chart.
                val barHeight = maxOf(baseline * fraction, if (point.total > 0) radius * 2 else 2.dp.toPx())
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundedTopRect(
                    left = left,
                    top = baseline - barHeight,
                    width = barWidth,
                    height = barHeight,
                    color = if (index == selectedIndex) ChartColors.Actual else ChartColors.Muted,
                    radius = radius
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                Text(
                    point.label,
                    color = if (index == selectedIndex) AppColors.OnSurface else AppColors.Secondary,
                    fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Share of spend
// ---------------------------------------------------------------------------------

/** One band of the share bar. [color] is assigned by the caller from the fixed order. */
data class ShareSlice(val label: String, val amount: Double, val color: Color)

/**
 * Where the money went, as one stacked bar over a legend.
 *
 * A stacked bar rather than a pie: comparing slices of a pie means comparing angles,
 * which people are measurably bad at, and the labels have nowhere to live. The legend
 * underneath carries name, amount and share for every band, so nothing on this chart is
 * available only to someone who can tell the colours apart.
 */
@Composable
fun SpendShareChart(
    slices: List<ShareSlice>,
    modifier: Modifier = Modifier,
    surface: Color = AppColors.Surface
) {
    val total = slices.sumOf { it.amount }
    if (slices.isEmpty() || total <= 0) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val radius = BAR_CORNER.toPx()
            val gap = SURFACE_GAP.toPx()
            var x = 0f
            slices.forEachIndexed { index, slice ->
                val width = (size.width * (slice.amount / total)).toFloat()
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(x, 0f, x + width, size.height),
                            // Only the two ends of the whole bar are rounded; the seams
                            // inside it stay square and are separated by the gap below.
                            topLeft = if (index == 0) CornerRadius(radius) else CornerRadius.Zero,
                            bottomLeft = if (index == 0) CornerRadius(radius) else CornerRadius.Zero,
                            topRight = if (index == slices.lastIndex) CornerRadius(radius) else CornerRadius.Zero,
                            bottomRight = if (index == slices.lastIndex) CornerRadius(radius) else CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, slice.color)
                // The separator is the surface showing through, not a stroke around the
                // segment — a stroke would add ink that is not data.
                if (index != slices.lastIndex && width > gap) {
                    drawRect(
                        color = surface,
                        topLeft = Offset(x + width - gap, 0f),
                        size = androidx.compose.ui.geometry.Size(gap, size.height)
                    )
                }
                x += width
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEach { slice ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(slice.color, CircleShape)
                    )
                    Text(
                        slice.label,
                        color = AppColors.OnSurface,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${((slice.amount / total) * 100).toInt()}%",
                        color = AppColors.Secondary,
                        fontSize = 11.sp
                    )
                    Text(
                        slice.amount.formatMoney(),
                        color = AppColors.OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Top [limit] categories by spend, with the rest folded into one "Other" band.
 *
 * The fold is not a space-saving measure — it is the rule that keeps the palette honest.
 * A tenth category would need a tenth hue, and a generated hue is indistinguishable from
 * an existing one for a colourblind reader.
 */
fun shareSlices(categories: List<CategoryBreakdown>, limit: Int = 5): List<ShareSlice> {
    val spent = categories.filter { it.actual > 0 }.sortedByDescending { it.actual }
    val head = spent.take(limit).mapIndexed { index, category ->
        ShareSlice(category.category, category.actual, ChartColors.categorical(index))
    }
    val tail = spent.drop(limit).sumOf { it.actual }
    return if (tail > 0) head + ShareSlice("Other", tail, ChartColors.Other) else head
}

// ---------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------

@Composable
private fun LegendKey(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 10.dp, height = 4.dp)
                .background(color, RoundedCornerShape(50))
        )
        Text(label, color = AppColors.Secondary, fontSize = 11.sp)
    }
}

/** A bar growing left to right: rounded on the right, square against the left baseline. */
private fun DrawScope.drawRoundedEndRect(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    radius: Float
) {
    val corner = CornerRadius(minOf(radius, width / 2f, height / 2f))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, left + width, top + height),
                topLeft = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
                topRight = corner,
                bottomRight = corner
            )
        )
    }
    drawPath(path, color)
}

/** A column growing upwards: rounded cap, square foot on the baseline. */
private fun DrawScope.drawRoundedTopRect(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    radius: Float
) {
    val corner = CornerRadius(minOf(radius, width / 2f, height / 2f))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, top, left + width, top + height),
                topLeft = corner,
                topRight = corner,
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero
            )
        )
    }
    drawPath(path, color)
}
