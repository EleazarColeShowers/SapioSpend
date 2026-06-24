package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
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
private val Warning = Color(0xFFD97706)

@Composable
fun EventDetailScreen(
    eventId: Int,
    onBack: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    eventViewModel: EventViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()

    val event = events.find { it.id == eventId }
    val expenses = allExpenses.filter { it.eventId == eventId }

    val totalSpent = expenses.sumOf { it.amount }
    val budget = event?.budget ?: 0.0
    val remaining = budget - totalSpent
    val progress = if (budget > 0) (totalSpent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val overBudget = totalSpent > budget

    val categoryTotals = expenses
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (event == null) {
        Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
            Text("Event not found", color = Secondary)
        }
        return
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Surface,
            title = { Text("Delete Event", color = OnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete \"${event.name}\" and all its expenses? This cannot be undone.", color = Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.deleteEvent(event)
                    showDeleteDialog = false
                    onBack()
                }) { Text("Delete", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Secondary)
                }
            }
        )
    }

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
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Secondary)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                event.name,
                                color = OnSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Text(event.eventType, color = Secondary, fontSize = 13.sp)
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete event", tint = Secondary)
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Budget Overview", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BudgetStat("Budget", "₦${budget.toInt()}", Color.White)
                            BudgetStat("Spent", "₦${totalSpent.toInt()}", Color(0xFFFF6B6B))
                            BudgetStat(
                                "Remaining",
                                "₦${remaining.toInt()}",
                                if (remaining >= 0) Color(0xFF6EE7B7) else Color(0xFFFF6B6B)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = when {
                                overBudget -> Color(0xFFFF6B6B)
                                progress > 0.8f -> Color(0xFFFBBF24)
                                else -> Color.White
                            },
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Text(
                            "${(progress * 100).toInt()}% used${if (overBudget) " · Over budget" else ""}",
                            color = if (overBudget) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (categoryTotals.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("By Category", color = Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                            categoryTotals.forEach { (category, amount) ->
                                val catProgress = if (budget > 0) (amount / budget).toFloat().coerceIn(0f, 1f) else 0f
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(category, color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("₦${amount.toInt()}", color = Secondary, fontSize = 13.sp)
                                    }
                                    LinearProgressIndicator(
                                        progress = { catProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = Black,
                                        trackColor = Border
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Expenses  ${expenses.size}",
                    color = Secondary,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(36.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = Border, modifier = Modifier.size(36.dp))
                            Text("No expenses yet", color = OnSurface, fontWeight = FontWeight.Medium)
                            Text("Tap + to record your first expense", color = Secondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(expense.title, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(expense.category, color = Secondary, fontSize = 12.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "₦${expense.amount.toInt()}",
                                    color = OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                IconButton(
                                    onClick = { eventViewModel.deleteExpense(expense) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete expense",
                                        tint = Border,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddExpense,
            containerColor = Black,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add expense")
        }
    }
}

@Composable
private fun BudgetStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}
