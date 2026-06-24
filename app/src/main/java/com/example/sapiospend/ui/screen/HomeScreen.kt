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
import com.example.sapiospend.ui.viewmodel.EventViewModel

private val BG = Color(0xFFF5F5F5)
private val Surface = Color(0xFFFFFFFF)
private val OnSurface = Color(0xFF111111)
private val Secondary = Color(0xFF6B7280)
private val Border = Color(0xFFE5E7EB)
private val Black = Color(0xFF111111)
private val Success = Color(0xFF16A34A)
private val Danger = Color(0xFFDC2626)

@Composable
fun HomeScreen(
    onAddEventClick: () -> Unit = {},
    onEventClick: (Int) -> Unit = {},
    eventViewModel: EventViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()

    val totalBudget = events.sumOf { it.budget }
    val totalSpent = allExpenses.sumOf { it.amount }
    val remaining = totalBudget - totalSpent

    Box(
        Modifier
            .fillMaxSize()
            .background(BG)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "SapioSpend",
                        color = OnSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text("Event budget planner", color = Secondary, fontSize = 14.sp)
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Black),
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
                            OverviewStat("Budget", "₦${totalBudget.toInt()}", Color.White)
                            OverviewStat("Spent", "₦${totalSpent.toInt()}", Color(0xFFFF6B6B))
                            OverviewStat(
                                "Remaining",
                                "₦${remaining.toInt()}",
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
                        colors = CardDefaults.cardColors(containerColor = Surface),
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
                                tint = Border,
                                modifier = Modifier.size(40.dp)
                            )
                            Text("No events yet", color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tap + to create your first event",
                                color = Secondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Events  ${events.size}",
                        color = Secondary,
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
                        colors = CardDefaults.cardColors(containerColor = Surface),
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
                                        color = OnSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(event.eventType, color = Secondary, fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (overBudget) {
                                        Box(
                                            Modifier
                                                .background(Danger.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("Over budget", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Border, modifier = Modifier.size(20.dp))
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                MiniStat("Budget", "₦${event.budget.toInt()}", Modifier.weight(1f))
                                MiniStat("Spent", "₦${eventSpent.toInt()}", Modifier.weight(1f))
                                MiniStat(
                                    "Left",
                                    "₦${eventRemaining.toInt()}",
                                    Modifier.weight(1f),
                                    valueColor = if (overBudget) Danger else Success
                                )
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = if (overBudget) Danger else Black,
                                trackColor = Border
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddEventClick,
            containerColor = Black,
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
    valueColor: Color = OnSurface
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Secondary, fontSize = 10.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
