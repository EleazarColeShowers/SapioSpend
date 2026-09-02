package com.el.sapiospend.ui.screen

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.el.sapiospend.domain.search.GlobalSearch
import com.el.sapiospend.export.ReportBuilder
import com.el.sapiospend.ui.component.ExportMenu
import com.el.sapiospend.ui.component.PaywallTrigger
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.ExportViewModel
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatPeriod
import com.el.sapiospend.util.formatMoney

@Composable
fun HomeScreen(
    onAddEventClick: () -> Unit = {},
    onEventClick: (String) -> Unit = {},
    /** Opens one expense for correction, from a search result. */
    onExpenseClick: (String) -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRequirePro: (PaywallTrigger) -> Unit = {},
    eventViewModel: EventViewModel,
    exportViewModel: ExportViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val budgetLines by eventViewModel.budgetLines.collectAsState()
    val contributions by eventViewModel.contributions.collectAsState()
    val plan by eventViewModel.plan.collectAsState()
    val remainingFreeEvents by eventViewModel.remainingFreeEvents.collectAsState()
    val isExporting by exportViewModel.isExporting.collectAsState()
    val focusManager = LocalFocusManager.current

    val proUnlocked = PlanRules.proFeaturesUnlocked(plan)

    val totalBudget = events.sumOf { it.budget }
    val totalSpent = allExpenses.sumOf { it.amount }
    val remaining = totalBudget - totalSpent
    val outstanding = allExpenses.sumOf { it.outstanding }

    /**
     * Screen state, not ViewModel state: leaving Home should forget the query rather
     * than restore it. The matching itself lives in [GlobalSearch], which the event
     * screen's own search shares the rules with.
     */
    var query by remember { mutableStateOf("") }
    val results = remember(query, events, allExpenses) {
        GlobalSearch.search(events, allExpenses, query)
    }
    val searching = query.isNotBlank()

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
                            if (proUnlocked) {
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
                            proUnlocked = proUnlocked,
                            isExporting = isExporting,
                            onExport = { format ->
                                exportViewModel.export(
                                    ReportBuilder.forAllEvents(events, allExpenses, budgetLines, contributions),
                                    format
                                )
                            },
                            onRequirePro = { onRequirePro(PaywallTrigger.EXPORT) }
                        )
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppColors.Secondary)
                        }
                    }
                }
            }


            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search events, vendors, expenses", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.Border, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searching) {
                            IconButton(onClick = {
                                query = ""
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = AppColors.Secondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.OnSurface,
                        unfocusedTextColor = AppColors.OnSurface,
                        focusedPlaceholderColor = AppColors.Border,
                        unfocusedPlaceholderColor = AppColors.Border,
                        focusedBorderColor = AppColors.Black,
                        unfocusedBorderColor = AppColors.Border,
                        cursorColor = AppColors.Black,
                        focusedContainerColor = AppColors.Surface,
                        unfocusedContainerColor = AppColors.Surface
                    )
                )
            }

            // A live query replaces the dashboard rather than appearing under it: the
            // totals below are for every event, and leaving them on screen next to a
            // filtered list is how a user comes to read one as the sum of the other.
            if (searching) {
                if (results.isEmpty) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(
                                Modifier.padding(28.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Nothing matches \"${query.trim()}\"", color = AppColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("Searched event names, expense titles, vendors, categories and notes", color = AppColors.Secondary, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    if (results.events.isNotEmpty()) {
                        item {
                            Text("Events  ${results.events.size}", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        }
                        items(results.events, key = { "event-" + it.id }) { event ->
                            val spent = allExpenses.filter { it.eventId == event.id }.sumOf { it.amount }
                            ResultRow(
                                title = event.name,
                                subtitle = "${event.eventType} · ${spent.formatMoney()} of ${event.budget.formatMoney()}",
                                trailing = null,
                                onClick = { onEventClick(event.id) }
                            )
                        }
                    }
                    if (results.expenses.isNotEmpty()) {
                        item {
                            Text(
                                // The cap is stated rather than silently applied: a
                                // planner searching "deposit" needs to know they are
                                // looking at the first forty and not at all of them.
                                if (results.expenses.size >= GlobalSearch.MAX_EXPENSE_HITS)
                                    "Expenses  first ${results.expenses.size}"
                                else "Expenses  ${results.expenses.size}",
                                color = AppColors.Secondary,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        items(results.expenses, key = { "expense-" + it.expense.id }) { hit ->
                            ResultRow(
                                title = hit.expense.title,
                                subtitle = buildString {
                                    append(hit.eventName)
                                    append(" · ")
                                    append(hit.expense.category)
                                    if (hit.expense.vendor.isNotBlank()) append(" · ${hit.expense.vendor}")
                                    if (!hit.expense.isSettled) append(" · ${hit.expense.outstanding.formatMoney()} owing")
                                },
                                trailing = hit.expense.amount.formatMoney(),
                                onClick = { onExpenseClick(hit.expense.id) }
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            // Only shown while the cap is actually close, so it reads as a heads-up
            // rather than a permanent nag.
            if (!proUnlocked && remainingFreeEvents in 0..1) {
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
                            OverviewStat("Budget", totalBudget.formatMoney(), Color.White)
                            OverviewStat("Spent", totalSpent.formatMoney(), Color(0xFFFF6B6B))
                            OverviewStat(
                                "Remaining",
                                remaining.formatMoney(),
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
                            buildString {
                                append("${if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0}% of total budget used")
                                // Only when there is a balance: on an app where every
                                // expense is paid the moment it is logged, a permanent
                                // "₦0 still to pay" is a line that never says anything.
                                if (outstanding > 0) append(" · ${outstanding.formatMoney()} still to pay")
                            },
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
                                        // Dated budgets lead with their period; undated
                                        // ones keep the creation date they always had.
                                        "${event.eventType} · ${formatPeriod(event.startDate, event.endDate) ?: event.dateCreated.formatDate()}",
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
                                MiniStat("Budget", event.budget.formatMoney(), Modifier.weight(1f))
                                MiniStat("Spent", eventSpent.formatMoney(), Modifier.weight(1f))
                                MiniStat(
                                    "Left",
                                    eventRemaining.formatMoney(),
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
                if (proUnlocked || remainingFreeEvents != 0) onAddEventClick()
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

/** One search hit: the same shape whether it is an event or an expense. */
@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = AppColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, color = AppColors.Secondary, fontSize = 12.sp)
            }
            trailing?.let {
                Text(it, color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
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
