package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.template.EventTypes
import com.el.sapiospend.export.ReportBuilder
import com.el.sapiospend.ui.component.ExportMenu
import com.el.sapiospend.ui.component.PeriodCalendarDialog
import com.el.sapiospend.ui.component.PlannedVsActualChart
import com.el.sapiospend.ui.component.PaywallTrigger
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.ExportViewModel
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatPeriod
import com.el.sapiospend.util.formatNaira

@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onEditPlan: () -> Unit = {},
    onEditExpense: (String) -> Unit = {},
    onRequirePro: (PaywallTrigger) -> Unit = {},
    eventViewModel: EventViewModel,
    exportViewModel: ExportViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val allBudgetLines by eventViewModel.budgetLines.collectAsState()
    val plan by eventViewModel.plan.collectAsState()
    val isExporting by exportViewModel.isExporting.collectAsState()

    val proUnlocked = PlanRules.proFeaturesUnlocked(plan)

    val event = events.find { it.id == eventId }
    val expenses = allExpenses.filter { it.eventId == eventId }

    val totalSpent = expenses.sumOf { it.amount }
    val budget = event?.budget ?: 0.0
    val remaining = budget - totalSpent
    val progress = if (budget > 0) (totalSpent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val overBudget = totalSpent > budget

    // When the event was created from a template there are planned amounts to compare
    // against; otherwise this collapses to a plain actual-spend breakdown.
    val analytics = remember(event, allExpenses, allBudgetLines) {
        event?.let { BudgetAnalytics.forEvent(it, allExpenses, allBudgetLines) }
    }
    val categories = analytics?.categories.orEmpty()
    val hasPlan = categories.any { it.planned > 0 }

    var showDeleteEventDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editBudget by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("Birthday") }
    var editStart by remember { mutableStateOf<Long?>(null) }
    var editEnd by remember { mutableStateOf<Long?>(null) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    val eventTypes = EventTypes.ALL

    if (event == null) {
        Box(Modifier.fillMaxSize().background(AppColors.BG), contentAlignment = Alignment.Center) {
            Text("Event not found", color = AppColors.Secondary)
        }
        return
    }

    if (showDeleteEventDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteEventDialog = false },
            containerColor = AppColors.Surface,
            title = { Text("Delete Event", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete \"${event.name}\" and all its expenses? This cannot be undone.", color = AppColors.Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.deleteEvent(event)
                    showDeleteEventDialog = false
                    onBack()
                }) { Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEventDialog = false }) {
                    Text("Cancel", color = AppColors.Secondary)
                }
            }
        )
    }

    expenseToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            containerColor = AppColors.Surface,
            title = { Text("Delete Expense", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("Remove \"${pending.title}\"? This cannot be undone.", color = AppColors.Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.deleteExpense(pending)
                    expenseToDelete = null
                }) { Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel", color = AppColors.Secondary)
                }
            }
        )
    }

    if (showPeriodPicker) {
        PeriodCalendarDialog(
            initialStart = editStart,
            initialEnd = editEnd,
            onDismiss = { showPeriodPicker = false },
            onConfirm = { start, end ->
                editStart = start
                editEnd = end
                showPeriodPicker = false
            }
        )
    }

    if (showEditDialog) {
        val editBudgetValue = editBudget.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = AppColors.Surface,
            title = { Text("Edit Event", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    // The dialog now carries a period row on top of three fields, which
                    // is taller than a short screen with the keyboard up.
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Event Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = editBudget,
                        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) editBudget = v },
                        label = { Text("Total Budget (₦)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {})
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Budget Period", color = AppColors.Secondary, fontSize = 12.sp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(AppColors.BG, RoundedCornerShape(12.dp))
                                .clickable { showPeriodPicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatPeriod(editStart, editEnd) ?: "No dates",
                                color = if (editStart != null) AppColors.OnSurface else AppColors.Secondary,
                                fontSize = 13.sp
                            )
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Change budget period",
                                tint = AppColors.Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        // Dropping the dates has to be reachable: an event given a period
                        // by mistake would otherwise be stuck reporting days-left and
                        // safe-daily-spend figures for a budget that has no deadline.
                        if (editStart != null || editEnd != null) {
                            TextButton(
                                onClick = {
                                    editStart = null
                                    editEnd = null
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Remove dates", color = AppColors.Secondary, fontSize = 12.sp)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Event Type", color = AppColors.Secondary, fontSize = 12.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            eventTypes.forEach { type ->
                                FilterChip(
                                    selected = editType == type,
                                    onClick = { editType = type },
                                    label = { Text(type, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppColors.BG,
                                        labelColor = AppColors.Secondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = editType == type,
                                        borderColor = AppColors.Border,
                                        selectedBorderColor = AppColors.Black
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank() && editBudgetValue > 0) {
                            eventViewModel.updateEvent(
                                event.copy(
                                    name = editName.trim(),
                                    budget = editBudgetValue,
                                    eventType = editType,
                                    startDate = editStart,
                                    endDate = editEnd
                                )
                            )
                            showEditDialog = false
                        }
                    },
                    enabled = editName.isNotBlank() && editBudgetValue > 0
                ) { Text("Save", color = AppColors.Black, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = AppColors.Secondary)
                }
            }
        )
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.Secondary)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                event.name,
                                color = AppColors.OnSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                // The period is the more useful subtitle when there is
                                // one: for a salary month, when it ends beats when it
                                // was created.
                                "${event.eventType} · ${formatPeriod(event.startDate, event.endDate) ?: event.dateCreated.formatDate()}",
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Row {
                        ExportMenu(
                            proUnlocked = proUnlocked,
                            isExporting = isExporting,
                            onExport = { format ->
                                exportViewModel.export(
                                    ReportBuilder.forEvent(event, allExpenses, allBudgetLines),
                                    format
                                )
                            },
                            onRequirePro = { onRequirePro(PaywallTrigger.EXPORT) }
                        )
                        IconButton(onClick = {
                            editName = event.name
                            editBudget = if (event.budget == kotlin.math.floor(event.budget)) "%.0f".format(event.budget) else event.budget.toString()
                            editType = event.eventType
                            editStart = event.startDate
                            editEnd = event.endDate
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit event", tint = AppColors.Secondary)
                        }
                        IconButton(onClick = { showDeleteEventDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete event", tint = AppColors.Secondary)
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Black),
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
                            BudgetStat("Budget", budget.formatNaira(), Color.White)
                            BudgetStat("Spent", totalSpent.formatNaira(), Color(0xFFFF6B6B))
                            BudgetStat(
                                "Remaining",
                                remaining.formatNaira(),
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

                        // The day-to-day line: what is still spendable per day, and
                        // whether the money is outrunning the calendar. Absent entirely
                        // for an open-ended event, which has no calendar to outrun.
                        val daysLeft = analytics?.daysRemaining
                        val safeDaily = analytics?.safeDailySpend
                        if (daysLeft != null && safeDaily != null) {
                            Text(
                                buildString {
                                    append(if (analytics.isPeriodOver) "Period closed" else "$daysLeft days left")
                                    append(" · ")
                                    append(
                                        if (safeDaily > 0) "${safeDaily.formatNaira()} a day left to spend"
                                        else "nothing left for the rest of the period"
                                    )
                                },
                                color = when {
                                    safeDaily <= 0 -> Color(0xFFFF6B6B)
                                    analytics.isSpendingAheadOfPace -> Color(0xFFFBBF24)
                                    else -> Color(0xFF6EE7B7)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (hasPlan) "Planned vs Actual" else "By Category",
                                color = AppColors.Secondary,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                            TextButton(onClick = onEditPlan, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(
                                    if (hasPlan) "Edit plan" else "Set a plan",
                                    color = AppColors.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (categories.isEmpty()) {
                            // Nothing planned and nothing spent. The useful thing to
                            // offer here is the plan, not an empty chart — a budget with
                            // no breakdown behind it can only ever report a total.
                            Text(
                                "Set what you intend to spend on each category, and every figure on this screen gets something to be measured against.",
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        } else {
                            PlannedVsActualChart(categories = categories)
                        }
                    }
                }
            }

            item {
                Text(
                    "Expenses  ${expenses.size}",
                    color = AppColors.Secondary,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
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
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = AppColors.Border, modifier = Modifier.size(36.dp))
                            Text("No expenses yet", color = AppColors.OnSurface, fontWeight = FontWeight.Medium)
                            Text("Tap + to record your first expense", color = AppColors.Secondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(12.dp),
                        // The whole row opens the expense for correction. A mistyped
                        // amount used to mean deleting the row and typing it all again,
                        // which threw away the record of the expense to fix a digit.
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditExpense(expense.id) },
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(expense.title, color = AppColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    "${expense.category} · ${expense.dateCreated.formatDate()}",
                                    color = AppColors.Secondary,
                                    fontSize = 12.sp
                                )
                                if (expense.notes.isNotBlank()) {
                                    Text(expense.notes, color = AppColors.Secondary.copy(alpha = 0.7f), fontSize = 11.sp)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    expense.amount.formatNaira(),
                                    color = AppColors.OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                IconButton(
                                    onClick = { expenseToDelete = expense },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete expense",
                                        tint = AppColors.Border,
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
            containerColor = AppColors.Black,
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
