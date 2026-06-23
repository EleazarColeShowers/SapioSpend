package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sapiospend.ui.viewmodel.EventViewModel

private val Navy = Color(0xFF0F172A)
private val NavyLight = Color(0xFF1E293B)
private val Slate = Color(0xFF334155)
private val SlateText = Color(0xFF94A3B8)
private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val Success = Color(0xFF10B981)
private val Danger = Color(0xFFEF4444)

@Composable
fun HomeScreen(
    onAddEventClick: () -> Unit = {},
    eventViewModel: EventViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val totalBudget = events.sumOf { it.budget }
    val totalSpent = 0.0
    val remaining = totalBudget - totalSpent

    Box(
        Modifier
            .fillMaxSize()
            .background(Navy)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Column {
                    Text(
                        "SapioSpend",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track your event budgets",
                        color = SlateText,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(NavyLight),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Overview", color = SlateText, fontSize = 13.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OverviewStat("Total Budget", "₦${totalBudget.toInt()}", AccentSoft)
                            OverviewStat("Spent", "₦${totalSpent.toInt()}", Danger)
                            OverviewStat(
                                "Remaining",
                                "₦${remaining.toInt()}",
                                if (remaining >= 0) Success else Danger
                            )
                        }
                        HorizontalDivider(color = Slate, thickness = 0.5.dp)
                        LinearProgressIndicator(
                            progress = { if (totalBudget > 0) (totalSpent / totalBudget).toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Accent,
                            trackColor = Slate
                        )
                        Text(
                            "${if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0}% of budget used",
                            color = SlateText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(NavyLight),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                tint = Accent,
                                modifier = Modifier.size(40.dp)
                            )
                            Text("No events yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tap + to start tracking your first event budget",
                                color = SlateText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    Text("Your Events", color = SlateText, fontSize = 13.sp)
                }
                items(events) { event ->
                    Card(
                        colors = CardDefaults.cardColors(NavyLight),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(event.name, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("₦${event.budget.toInt()}", color = AccentSoft, fontSize = 13.sp)
                            }
                            Box(
                                Modifier
                                    .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Active", color = AccentSoft, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddEventClick,
            containerColor = Accent,
            contentColor = Color.White,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = SlateText, fontSize = 11.sp)
    }
}