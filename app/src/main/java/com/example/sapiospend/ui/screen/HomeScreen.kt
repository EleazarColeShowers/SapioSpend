package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sapiospend.billing.Plan
import com.example.sapiospend.export.ExportFormat
import com.example.sapiospend.export.ReportBuilder
import com.example.sapiospend.ui.component.ExportMenu
import com.example.sapiospend.ui.component.PaywallTrigger
import com.example.sapiospend.ui.theme.AppColors
import com.example.sapiospend.ui.viewmodel.EventViewModel
import com.example.sapiospend.ui.viewmodel.ExportViewModel
import com.example.sapiospend.util.formatDate
import com.example.sapiospend.util.formatNaira

@Composable
fun HomeScreen(
    onAddEventClick: () -> Unit = {},
    onEventClick: (String) -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onRequirePro: (PaywallTrigger) -> Unit = {},
    eventViewModel: EventViewModel,
    exportViewModel: ExportViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val budgetLines by eventViewModel.budgetLines.collectAsState()
    val plan by eventViewModel.plan.collectAsState()
    val remainingFreeEvents by eventViewModel.remainingFreeEvents.collectAsState()
    val isExporting by exportViewModel.isExporting.collectAsState()

    val isPro = plan == Plan.PRO

    val totalBudget = events.sumOf { it.budget }
    val totalSpent = allExpenses.sumOf { it.amount }
    val remaining = totalBudget - totalSpent

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
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "SapioSpend",
                                color = AppColors.OnSurface,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            if (isPro) {
                                Box(
                                    Modifier
                                        .background(AppColors.Black, RoundedCornerShape(5.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("PRO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                        Text("Event budget planner", color = AppColors.Secondary, fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAnalyticsClick) {
                            Icon(Icons.Default.BarChart, contentDescription = "Analytics", tint = AppColors.Secondary)
                        }
                        ExportMenu(
                            isPro = isPro,
                            isExporting = isExporting,
                            onExport = { format ->
                                exportViewModel.export(
                                    ReportBuilder.forAllEvents(events, allExpenses, budgetLines),
                                    format
                                )
                            },
                            onRequirePro = { onRequirePro(PaywallTrigger.EXPORT) }
                        )
                    }
                }
            }

            // Only shown while the cap is actually close, so it reads as a heads-up
            // rather than a permanent nag.
            if (!isPro && remainingFreeEvents in 0..1) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Warning.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (remainingFreeEvents == 0) "You've used all 3 free events"
                                else "1 free event left",
                                color = AppColors.Warning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Upgrade",
                                color = AppColors.Warning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onRequirePro(PaywallTrigger.EVENT_LIMIT) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Overview", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OverviewStat("Budget", totalBudget.formatNaira(), Color.White)
                            OverviewStat("Spent", totalSpent.formatNaira(), Color(0xFFFF6B6B))
                            OverviewStat(
                                "Remaining",
                                remaining.formatNaira(),
                                if (remaining >= 0) Color(0xFF6EE7B7) else Color(0xFFFF6B6B)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (totalSpent > totalBudget) Color(0xFFFF6B6B) else Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Text(
                            "${if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0}% of total budget used",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
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
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = AppColors.Border,
                                modifier = Modifier.size(40.dp)
                            )
                            Text("No events yet", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tap + to create your first event",
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Events  ${events.size}",
                        color = AppColors.Secondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                items(events) { event ->
                    val eventSpent = allExpenses.filter { it.eventId == event.id }.sumOf { it.amount }
                    val eventRemaining = event.budget - eventSpent
                    val progress = if (event.budget > 0) (eventSpent / event.budget).toFloat().coerceIn(0f, 1f) else 0f
                    val overBudget = eventSpent > event.budget

                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEventClick(event.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        event.name,
                                        color = AppColors.OnSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "${event.eventType} · ${event.dateCreated.formatDate()}",
                                        color = AppColors.Secondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (overBudget) {
                                        Box(
                                            Modifier
                                                .background(AppColors.Danger.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("Over budget", color = AppColors.Danger, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.Border, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(Modifier.fillMaxWidth()) {
                                MiniStat("Budget", event.budget.formatNaira(), Modifier.weight(1f))
                                MiniStat("Spent", eventSpent.formatNaira(), Modifier.weight(1f))
                                MiniStat(
                                    "Left",
                                    eventRemaining.formatNaira(),
                                    Modifier.weight(1f),
                                    valueColor = if (overBudget) AppColors.Danger else AppColors.Success
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (overBudget) AppColors.Danger else AppColors.Black,
                                trackColor = AppColors.Border
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            // Checked here as well as in the ViewModel so a capped user meets the paywall
            // before filling in a form that cannot be saved.
            onClick = {
                if (isPro || remainingFreeEvents != 0) onAddEventClick()
                else onRequirePro(PaywallTrigger.EVENT_LIMIT)
            },
            containerColor = AppColors.Black,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add event")
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun MiniStat(
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
