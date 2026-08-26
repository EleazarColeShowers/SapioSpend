package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.analytics.SpendTrend
import com.el.sapiospend.ui.component.PlannedVsActualChart
import com.el.sapiospend.ui.component.SpendShareChart
import com.el.sapiospend.ui.component.SpendTrendChart
import com.el.sapiospend.ui.component.shareSlices
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.util.formatMoney

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit = {},
    onEventClick: (String) -> Unit = {},
    eventViewModel: EventViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val budgetLines by eventViewModel.budgetLines.collectAsState()

    // Recomputed only when the underlying data changes rather than on every recomposition
    // — this walks every expense of every event.
    val portfolio = remember(events, allExpenses, budgetLines) {
        BudgetAnalytics.portfolio(events, allExpenses, budgetLines)
    }
    val monthlySpend = remember(allExpenses) { SpendTrend.monthly(allExpenses) }
    val shareOfSpend = remember(portfolio) { shareSlices(portfolio.topCategories) }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.Secondary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Analytics",
                            color = AppColors.OnSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            if (portfolio.eventCount == 1) "Across 1 event"
                            else "Across all ${portfolio.eventCount} events",
                            color = AppColors.Secondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(40.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Insights, contentDescription = null, tint = AppColors.Border, modifier = Modifier.size(40.dp))
                            Text("Nothing to analyse yet", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Create an event and log some expenses", color = AppColors.Secondary, fontSize = 13.sp)
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Portfolio", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PortfolioStat("Budget", portfolio.totalBudget.formatMoney(), Color.White)
                            PortfolioStat("Spent", portfolio.totalSpent.formatMoney(), Color(0xFFFF6B6B))
                            PortfolioStat(
                                "Remaining",
                                portfolio.totalRemaining.formatMoney(),
                                if (portfolio.totalRemaining >= 0) Color(0xFF6EE7B7) else Color(0xFFFF6B6B)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { portfolio.percentUsed.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (portfolio.totalSpent > portfolio.totalBudget) Color(0xFFFF6B6B) else Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Text(
                            "${(portfolio.percentUsed * 100).toInt()}% used · ${portfolio.overBudgetCount} of ${portfolio.eventCount} over budget",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // The trend goes first: before anyone asks where the money went, they want
            // to know whether it is going out faster than it used to.
            item {
                ChartCard("SPEND OVER TIME") {
                    SpendTrendChart(points = monthlySpend)
                    Text(
                        "Tap a month to read its total",
                        color = AppColors.Border,
                        fontSize = 11.sp
                    )
                }
            }

            if (portfolio.topCategories.isNotEmpty()) {
                item {
                    ChartCard(if (portfolio.topCategories.any { it.planned > 0 }) "PLANNED VS ACTUAL" else "WHERE THE MONEY GOES") {
                        PlannedVsActualChart(categories = portfolio.topCategories)
                    }
                }

                item {
                    ChartCard("SHARE OF SPEND") {
                        SpendShareChart(slices = shareOfSpend)
                    }
                }
            }

            item {
                Text("By Event", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
            }

            items(portfolio.events, key = { it.eventId }) { analytics ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEventClick(analytics.eventId) },
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                analytics.eventName,
                                color = AppColors.OnSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(analytics.percentUsed * 100).toInt()}%",
                                color = if (analytics.isOverBudget) AppColors.Danger else AppColors.Secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(Modifier.fillMaxWidth()) {
                            AnalyticsStat("Spent", analytics.totalSpent.formatMoney(), Modifier.weight(1f))
                            AnalyticsStat("Per day", analytics.dailyBurnRate.formatMoney(), Modifier.weight(1f))
                            AnalyticsStat(
                                "Left",
                                analytics.remaining.formatMoney(),
                                Modifier.weight(1f),
                                valueColor = if (analytics.isOverBudget) AppColors.Danger else AppColors.Success
                            )
                        }

                        // Only budgets with an end date get a second row: for an
                        // open-ended event these figures do not exist, and inventing
                        // them would be worse than leaving the space empty.
                        val daysLeft = analytics.daysRemaining
                        val safeDaily = analytics.safeDailySpend
                        if (daysLeft != null && safeDaily != null) {
                            Row(Modifier.fillMaxWidth()) {
                                AnalyticsStat(
                                    if (analytics.isPeriodOver) "Period" else "Days left",
                                    if (analytics.isPeriodOver) "Closed" else "$daysLeft",
                                    Modifier.weight(1f)
                                )
                                AnalyticsStat(
                                    "Safe per day",
                                    // A negative allowance is nonsense to display; once
                                    // the money is gone the honest figure is zero.
                                    maxOf(safeDaily, 0.0).formatMoney(),
                                    Modifier.weight(1f),
                                    valueColor = if (safeDaily <= 0) AppColors.Danger else AppColors.OnSurface
                                )
                                AnalyticsStat(
                                    "Pace",
                                    if (analytics.isSpendingAheadOfPace) "Ahead" else "On track",
                                    Modifier.weight(1f),
                                    valueColor = if (analytics.isSpendingAheadOfPace) AppColors.Danger else AppColors.Success
                                )
                            }

                            analytics.projectedOverspend?.let { overspend ->
                                Text(
                                    "At this pace you finish ${overspend.formatMoney()} over budget",
                                    color = AppColors.Danger,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        analytics.biggestOverrun?.let { overrun ->
                            Text(
                                "${overrun.category} is ${overrun.variance.formatMoney()} over plan",
                                color = AppColors.Danger,
                                fontSize = 11.sp
                            )
                        }

                        if (analytics.unallocated > 0 && analytics.totalPlanned > 0) {
                            Text(
                                "${analytics.unallocated.formatMoney()} of the budget is unallocated",
                                color = AppColors.Secondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The frame every chart on this screen sits in: a plain card and a quiet heading. */
@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = AppColors.Secondary, fontSize = 11.sp, letterSpacing = 0.5.sp)
            content()
        }
    }
}

@Composable
private fun PortfolioStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun AnalyticsStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColors.OnSurface
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = AppColors.Secondary, fontSize = 10.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
