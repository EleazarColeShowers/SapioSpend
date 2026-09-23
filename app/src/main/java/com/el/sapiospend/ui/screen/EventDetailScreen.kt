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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.data.local.RecurringExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.payment.PaymentStatus
import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.domain.recurring.Recurrence
import com.el.sapiospend.domain.search.ExpenseListing
import com.el.sapiospend.domain.search.ExpenseSort
import com.el.sapiospend.domain.search.ExpenseStatusFilter
import com.el.sapiospend.domain.search.ExpenseView
import com.el.sapiospend.domain.template.EventTypes
import com.el.sapiospend.R
import com.el.sapiospend.export.ReportBuilder
import com.el.sapiospend.ui.component.DayCalendarDialog
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.ui.component.ExportMenu
import com.el.sapiospend.ui.text.BudgetWords
import com.el.sapiospend.ui.component.OverviewStat
import com.el.sapiospend.ui.component.PeriodCalendarDialog
import com.el.sapiospend.ui.component.PlannedVsActualChart
import com.el.sapiospend.ui.component.PaywallTrigger
import com.el.sapiospend.ui.component.ReceiptImage
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.ExportViewModel
import com.el.sapiospend.util.DateUtils
import com.el.sapiospend.util.formatAmountInput
import com.el.sapiospend.util.parseAmount
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatPeriod
import com.el.sapiospend.util.formatConverted
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.settings.ActiveBase
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.settings.BudgetMoney
import com.el.sapiospend.ui.component.CurrencyPicker

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
    val focusManager = LocalFocusManager.current

    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val allBudgetLines by eventViewModel.budgetLines.collectAsState()
    val allContributions by eventViewModel.contributions.collectAsState()
    val allRecurringRules by eventViewModel.recurringRules.collectAsState()
    val plan by eventViewModel.plan.collectAsState()
    val isExporting by exportViewModel.isExporting.collectAsState()

    val proUnlocked = PlanRules.proFeaturesUnlocked(plan)

    val event = events.find { it.id == eventId }
    val expenses = remember(allExpenses, eventId) { allExpenses.filter { it.eventId == eventId } }

    val contributions = remember(allContributions, eventId) { allContributions.filter { it.eventId == eventId } }
    val recurringRules = remember(allRecurringRules, eventId) { allRecurringRules.filter { it.eventId == eventId } }

    /**
     * How the list is currently searched, filtered and sorted.
     *
     * Held here rather than in the ViewModel because it is screen state — leaving the
     * event should forget it, not restore it. The rules themselves live in
     * [ExpenseListing], so the same view could be applied to a report or a widget later.
     */
    var view by remember { mutableStateOf(ExpenseView()) }
    val visibleExpenses = remember(expenses, view) { ExpenseListing.apply(expenses, view) }
    val expenseCategories = remember(expenses) { ExpenseListing.categoriesOf(expenses) }

    val totalSpent = expenses.sumOf { it.amount }
    val budget = event?.budget ?: 0.0
    val remaining = budget - totalSpent
    val progress = if (budget > 0) (totalSpent / budget).toFloat().coerceIn(0f, 1f) else 0f

    /**
     * The words this budget is described in. A savings goal is not spending money, so
     * every label on this screen that says so comes from here instead of from a literal.
     */
    val words = BudgetWords.of(event?.direction ?: BudgetDirection.DEFAULT)
    val saving = words == BudgetWords.SAVING

    /**
     * The currency this budget is kept in, and how to write it down.
     *
     * Every figure on this screen belongs to this one budget, so all of them are in this
     * currency and none of them is converted — a $5,000 target reads as $5,000 for as
     * long as it exists. Where the user is reading the rest of the app in something
     * else, the overview carries a quieter converted line underneath.
     */
    val money = BudgetMoney.forCode(event?.currencyCode)

    /** Passing the total is a failure on a budget and the whole point of a goal. */
    val overBudget = totalSpent > budget && !saving

    // When the event was created from a template there are planned amounts to compare
    // against; otherwise this collapses to a plain actual-spend breakdown.
    val analytics = remember(event, allExpenses, allBudgetLines, allContributions) {
        event?.let { BudgetAnalytics.forEvent(it, allExpenses, allBudgetLines, allContributions, base = ActiveBase.value) }
    }
    val categories = analytics?.categories.orEmpty()
    val hasPlan = categories.any { it.planned > 0 }

    var showDeleteEventDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showAddContribution by remember { mutableStateOf(false) }
    var showAddRecurring by remember { mutableStateOf(false) }
    var contributionToDelete by remember { mutableStateOf<ContributionEntity?>(null) }
    var ruleToDelete by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editBudget by remember { mutableStateOf("") }
    var editGuests by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("Birthday") }
    var editDirection by remember { mutableStateOf(BudgetDirection.DEFAULT) }
    var editCurrency by remember { mutableStateOf(AppCurrency.DEFAULT) }
    /** Set once the user has asked to save a currency change and has to confirm it. */
    var pendingCurrencyChange by remember { mutableStateOf<EventEntity?>(null) }
    var editStart by remember { mutableStateOf<Long?>(null) }
    var editEnd by remember { mutableStateOf<Long?>(null) }
    var showPeriodPicker by remember { mutableStateOf(false) }

    val eventTypes = EventTypes.ALL

    if (event == null) {
        Box(Modifier.fillMaxSize().background(AppColors.BG), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_not_found), color = AppColors.Secondary)
        }
        return
    }

    if (showDeleteEventDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteEventDialog = false },
            containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.detail_delete_title), color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.detail_delete_message, event.name), color = AppColors.Secondary) },
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
            title = { Text(stringResource(words.deleteEntryTitle), color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
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

    contributionToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { contributionToDelete = null },
            containerColor = AppColors.Surface,
            title = { Text("Remove Contribution", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.detail_funding_remove, pending.source), color = AppColors.Secondary) },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.deleteContribution(pending)
                    contributionToDelete = null
                }) { Text("Remove", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { contributionToDelete = null }) { Text("Cancel", color = AppColors.Secondary) }
            }
        )
    }

    ruleToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            containerColor = AppColors.Surface,
            title = { Text("Delete Recurring Expense", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.detail_recurring_stop, pending.title),
                    color = AppColors.Secondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.deleteRecurringRule(pending)
                    ruleToDelete = null
                }) { Text("Delete", color = AppColors.Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel", color = AppColors.Secondary) }
            }
        )
    }

    if (showAddContribution) {
        AddContributionDialog(
            money = money,
            onDismiss = { showAddContribution = false },
            onAdd = { source, amount, received ->
                eventViewModel.addContribution(
                    eventId = eventId,
                    source = source,
                    amount = amount,
                    receivedAt = if (received) System.currentTimeMillis() else null
                )
                showAddContribution = false
            }
        )
    }

    if (showAddRecurring) {
        AddRecurringDialog(
            money = money,
            categories = (categories.map { it.category } + expenseCategories).distinct(),
            eventEnd = event.endDate,
            onDismiss = { showAddRecurring = false },
            onAdd = { title, category, amount, recurrence, startDate, vendor ->
                eventViewModel.addRecurringRule(
                    eventId = eventId,
                    title = title,
                    category = category,
                    amount = amount,
                    recurrence = recurrence,
                    startDate = startDate,
                    vendor = vendor,
                    // A rule on a dated event stops when the event does. Nobody sets up a
                    // weekly payment for a wedding meaning it to run into next year.
                    until = event.endDate
                )
                showAddRecurring = false
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

    pendingCurrencyChange?.let { edited ->
        val from = money.currency
        val to = AppCurrency.fromCode(edited.currencyCode, from)
        AlertDialog(
            onDismissRequest = { pendingCurrencyChange = null },
            containerColor = AppColors.Surface,
            title = {
                Text(
                    stringResource(R.string.currency_convert_title),
                    color = AppColors.OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.currency_convert_message,
                        edited.name,
                        expenses.size,
                        from.code,
                        to.code,
                        money.rates.unitRate(from, to).formatMoney(to, from = to, rates = money.rates)
                    ),
                    color = AppColors.Secondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    eventViewModel.updateEvent(edited, convertFrom = from, rates = money.rates)
                    pendingCurrencyChange = null
                }) {
                    Text(
                        stringResource(R.string.currency_convert_confirm),
                        color = AppColors.Danger,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCurrencyChange = null }) {
                    Text("Cancel", color = AppColors.Secondary)
                }
            }
        )
    }

    if (showEditDialog) {
        // Read in whatever the picker below currently says, which is what the field is
        // labelled with — not in the currency the budget is still stored in.
        val editBudgetValue = editBudget.parseAmount(money.copy(currency = editCurrency)) ?: 0.0
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.detail_edit_title), color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
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
                        label = { Text(stringResource(R.string.field_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = editBudget,
                        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) editBudget = v },
                        label = { Text(stringResource(words.totalField, editCurrency.symbol)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {})
                    )
                    CurrencyPicker(
                        selected = editCurrency,
                        display = ActiveCurrency.value,
                        rates = money.rates,
                        onSelect = { picked ->
                            if (picked != editCurrency) {
                                // The field is always read in the currency selected here,
                                // so the figure in it moves with the choice. Without this
                                // the user would watch "5000" stay put while the label
                                // under it changed from dollars to naira and quietly mean
                                // something a thousand times smaller.
                                editBudget = (editBudget.parseAmount(money.copy(currency = editCurrency)) ?: 0.0)
                                    .takeIf { it > 0 }
                                    ?.let { money.rates.convert(it, editCurrency, picked).formatAmountInput(money.copy(currency = picked)) }
                                    ?: editBudget
                                editCurrency = picked
                            }
                        }
                    )

                    OutlinedTextField(
                        value = editGuests,
                        onValueChange = { v -> if (v.all { it.isDigit() }) editGuests = v },
                        label = { Text("Guests (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.field_period), color = AppColors.Secondary, fontSize = 12.sp)
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
                                contentDescription = stringResource(R.string.detail_change_period),
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
                        Text(stringResource(R.string.field_type), color = AppColors.Secondary, fontSize = 12.sp)
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

                    // Direction is set by the template at creation, which leaves someone
                    // who started from a blank page with no way to say a budget is a
                    // savings goal — and someone who picked the savings template with no
                    // way to undo it. It is two chips; it belongs here.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.direction_title), color = AppColors.Secondary, fontSize = 12.sp)
                        BudgetDirection.entries.forEach { option ->
                            val selected = editDirection == option
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) AppColors.Black else AppColors.BG
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editDirection = option },
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            if (option.isSaving) R.string.direction_saving
                                            else R.string.direction_spending
                                        ),
                                        color = if (selected) Color.White else AppColors.OnSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(
                                            if (option.isSaving) R.string.direction_saving_blurb
                                            else R.string.direction_spending_blurb
                                        ),
                                        color = if (selected) Color.White.copy(alpha = 0.7f) else AppColors.Secondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank() && editBudgetValue > 0) {
                            val edited = event.copy(
                                name = editName.trim(),
                                budget = editBudgetValue,
                                eventType = editType,
                                moneyDirection = editDirection.name,
                                guestCount = editGuests.toIntOrNull()?.takeIf { it > 0 },
                                currencyCode = editCurrency.code,
                                startDate = editStart,
                                endDate = editEnd
                            )
                            // Changing the currency rewrites every figure on the budget,
                            // which is the one thing in this app that touches what the
                            // user recorded. It gets its own yes.
                            if (editCurrency != money.currency) pendingCurrencyChange = edited
                            else eventViewModel.updateEvent(edited)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = AppColors.Secondary)
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
                                    ReportBuilder.forEvent(event, allExpenses, allBudgetLines, allContributions, base = ActiveBase.value),
                                    format
                                )
                            },
                            onRequirePro = { onRequirePro(PaywallTrigger.EXPORT) }
                        )
                        IconButton(onClick = {
                            editName = event.name
                            editBudget = event.budget.formatAmountInput(money)
                            editCurrency = money.currency
                            editGuests = event.guestCount?.toString().orEmpty()
                            editType = event.eventType
                            editDirection = event.direction
                            editStart = event.startDate
                            editEnd = event.endDate
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.detail_edit), tint = AppColors.Secondary)
                        }
                        IconButton(onClick = { showDeleteEventDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.detail_delete), tint = AppColors.Secondary)
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
                        Text(stringResource(R.string.label_overview), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, letterSpacing = 0.5.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OverviewStat(
                                stringResource(words.total),
                                budget.formatMoney(money),
                                Color.White,
                                Modifier.weight(1f),
                                converted = budget.formatConverted(money)
                            )
                            // Money leaving is a warning colour; money arriving is not.
                            OverviewStat(
                                stringResource(words.spent),
                                totalSpent.formatMoney(money),
                                if (saving) Color(0xFF6EE7B7) else Color(0xFFFF6B6B),
                                Modifier.weight(1f),
                                converted = totalSpent.formatConverted(money)
                            )
                            OverviewStat(
                                stringResource(words.remaining),
                                remaining.coerceAtLeast(0.0).formatMoney(money),
                                if (remaining >= 0 || saving) Color(0xFF6EE7B7) else Color(0xFFFF6B6B),
                                Modifier.weight(1f),
                                converted = remaining.coerceAtLeast(0.0).formatConverted(money)
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
                            stringResource(words.progressPercent, (progress * 100).toInt()) +
                                if (overBudget) " · ${stringResource(R.string.label_over_budget)}" else "",
                            color = if (overBudget) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )

                        // The committed-versus-paid line. Absent when everything is
                        // settled, because on a budget with nothing outstanding it would
                        // only ever repeat the Spent figure back at the user.
                        val payments = analytics?.payments
                        if (words.showsFundingAndPayments && payments != null && payments.outstanding > 0) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OverviewStat(stringResource(R.string.label_paid), payments.paid.formatMoney(money), Color.White, Modifier.weight(1f))
                                OverviewStat(
                                    stringResource(R.string.label_still_owed),
                                    payments.outstanding.formatMoney(money),
                                    Color(0xFFFBBF24),
                                    Modifier.weight(1f)
                                )
                                OverviewStat(
                                    stringResource(R.string.label_overdue),
                                    if (payments.overdueCount > 0) payments.overdueAmount.formatMoney(money) else "None",
                                    if (payments.overdueCount > 0) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.6f),
                                    Modifier.weight(1f)
                                )
                            }
                            payments.nextDueDate?.let { due ->
                                // Same day-based rule the rows use, so the card and the
                                // list cannot disagree about whether a payment is late.
                                val late = Payments.isPastDue(due)
                                Text(
                                    if (late) "A payment was due ${due.formatDate()}"
                                    else "Next payment due ${due.formatDate()}",
                                    color = if (late) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        analytics?.costPerGuest?.let { perGuest ->
                            Text(
                                "${analytics.guestCount} guests · ${perGuest.formatMoney(money)} spent per guest" +
                                    (analytics.budgetPerGuest?.let { " of ${it.formatMoney(money)} budgeted" } ?: ""),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }

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
                                        when {
                                            safeDaily <= 0 && saving -> "on target"
                                            safeDaily <= 0 -> "nothing left for the rest of the period"
                                            saving -> "${safeDaily.formatMoney(money)} a day to reach it"
                                            else -> "${safeDaily.formatMoney(money)} a day left to spend"
                                        }
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
                                if (hasPlan) stringResource(R.string.categories_planned_vs_actual)
                                else stringResource(words.categoriesTitle),
                                color = AppColors.Secondary,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                            TextButton(onClick = onEditPlan, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(
                                    if (hasPlan) stringResource(words.categoriesEdit)
                                    else stringResource(words.categoriesSet),
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
                                stringResource(words.categoriesEmptyPrompt),
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        } else {
                            PlannedVsActualChart(categories = categories, money = money)
                        }
                    }
                }
            }

            // Who is paying for this is a question a savings goal has no answer to:
            // the money arriving *is* the budget, and a second tally of it beside the
            // first would be the same figure under two names.
            if (words.showsFundingAndPayments) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("FUNDING", color = AppColors.Secondary, fontSize = 11.sp, letterSpacing = 0.5.sp)
                            TextButton(
                                onClick = { showAddContribution = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Add", color = AppColors.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (contributions.isEmpty()) {
                            Text(
                                "Record client deposits, sponsors and contributions here, and the app can tell you what is actually in hand rather than only what the budget allows.",
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        } else {
                            val funding = analytics?.funding
                            Row(Modifier.fillMaxWidth()) {
                                MiniFigure(stringResource(R.string.label_received), (funding?.received ?: 0.0).formatMoney(money), Modifier.weight(1f), AppColors.Success)
                                MiniFigure(stringResource(R.string.label_pledged), (funding?.pledged ?: 0.0).formatMoney(money), Modifier.weight(1f))
                                MiniFigure(
                                    stringResource(R.string.label_cash_left),
                                    (analytics?.cashPosition ?: 0.0).formatMoney(money),
                                    Modifier.weight(1f),
                                    // Cash received less cash paid out. Negative means
                                    // the vendors already paid came out of somebody's own
                                    // pocket, which is the thing worth colouring red.
                                    if ((analytics?.cashPosition ?: 0.0) < 0) AppColors.Danger else AppColors.OnSurface
                                )
                            }

                            contributions.forEach { contribution ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(contribution.source, color = AppColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (contribution.isReceived)
                                                "Received ${(contribution.receivedAt ?: contribution.dateCreated).formatDate()}"
                                            else "Promised — not yet in",
                                            color = if (contribution.isReceived) AppColors.Secondary else AppColors.Warning,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        contribution.amount.formatMoney(money),
                                        color = AppColors.OnSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (!contribution.isReceived) {
                                        TextButton(
                                            onClick = { eventViewModel.setContributionReceived(contribution, true) },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Text("Received", color = AppColors.Black, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    IconButton(
                                        onClick = { contributionToDelete = contribution },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete contribution", tint = AppColors.Border, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            analytics?.let { a ->
                                val shortfall = a.funding.shortfall(a.budget)
                                if (shortfall > 0) {
                                    Text(
                                        stringResource(R.string.detail_unfunded, shortfall.formatMoney(money)),
                                        color = AppColors.Secondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("RECURRING", color = AppColors.Secondary, fontSize = 11.sp, letterSpacing = 0.5.sp)
                            TextButton(
                                onClick = { showAddRecurring = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Add", color = AppColors.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (recurringRules.isEmpty()) {
                            Text(
                                "A cost that comes back — weekly hire, a monthly retainer, rent. The app records each one as it falls due instead of you remembering to.",
                                color = AppColors.Secondary,
                                fontSize = 13.sp
                            )
                        } else {
                            recurringRules.forEach { rule ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Repeat,
                                        contentDescription = null,
                                        tint = if (rule.active) AppColors.Secondary else AppColors.Border,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(rule.title, color = AppColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            buildString {
                                                append(Recurrence.fromName(rule.frequency).label)
                                                append(" · ")
                                                append(if (rule.active) "next ${rule.nextDueDate.formatDate()}" else "paused")
                                            },
                                            color = AppColors.Secondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(rule.amount.formatMoney(money), color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Switch(
                                        checked = rule.active,
                                        onCheckedChange = { eventViewModel.setRecurringActive(rule, it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AppColors.Black,
                                            uncheckedThumbColor = AppColors.Secondary,
                                            uncheckedTrackColor = AppColors.Border
                                        )
                                    )
                                    IconButton(
                                        onClick = { ruleToDelete = rule },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete recurring expense", tint = AppColors.Border, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Text(
                                // Said once, because the alternative — a user who thinks
                                // deleting the rule undoes the charges — is a support
                                // ticket about money going missing.
                                "Expenses already recorded by a rule stay put when it is paused or deleted.",
                                color = AppColors.Border,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // While the list is narrowed the count says how much of it is
                            // hidden, so a filtered view can never be mistaken for the
                            // whole record of what was spent.
                            if (!view.isNarrowed) stringResource(words.entriesCount, expenses.size)
                            else stringResource(words.entriesNarrowed, visibleExpenses.size, expenses.size),
                            color = AppColors.Secondary,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        if (expenses.isNotEmpty()) {
                            SortMenu(current = view.sort, onPick = { view = view.copy(sort = it) })
                        }
                    }

                    if (expenses.isNotEmpty()) {
                        OutlinedTextField(
                            value = view.query,
                            onValueChange = { view = view.copy(query = it) },
                            placeholder = { Text(stringResource(words.searchHint), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = AppColors.Border,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (view.query.isNotEmpty()) {
                                    IconButton(onClick = { view = view.copy(query = "") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = AppColors.Secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
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

                        // Status first, then categories. Both are one row of chips rather
                        // than a filter sheet: with three states and a handful of
                        // categories, a sheet would be a screen to open in order to tap
                        // the thing that already fits on this one.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExpenseStatusFilter.entries.forEach { status ->
                                val count = when (status) {
                                    ExpenseStatusFilter.ALL -> expenses.size
                                    ExpenseStatusFilter.OUTSTANDING -> expenses.count { !it.isSettled }
                                    ExpenseStatusFilter.OVERDUE -> expenses.count { Payments.isOverdue(it) }
                                    ExpenseStatusFilter.PAID -> expenses.count { it.isSettled }
                                }
                                // A filter that would empty the list is not offered —
                                // except All, which is how you get back.
                                if (count == 0 && status != ExpenseStatusFilter.ALL) return@forEach
                                SmallChip(
                                    label = "${status.label} $count",
                                    selected = view.status == status,
                                    onClick = { view = view.copy(status = status) },
                                    danger = status == ExpenseStatusFilter.OVERDUE
                                )
                            }
                        }

                        if (expenseCategories.size > 1) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SmallChip(
                                    label = "All categories",
                                    selected = view.category == null,
                                    onClick = { view = view.copy(category = null) }
                                )
                                expenseCategories.forEach { category ->
                                    SmallChip(
                                        label = category,
                                        selected = view.category == category,
                                        onClick = {
                                            view = view.copy(category = if (view.category == category) null else category)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
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
                            Text(stringResource(words.emptyTitle), color = AppColors.OnSurface, fontWeight = FontWeight.Medium)
                            Text(stringResource(words.emptySubtitle), color = AppColors.Secondary, fontSize = 13.sp)
                        }
                    }
                }
            } else if (visibleExpenses.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            Modifier
                                .padding(28.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (view.query.isNotBlank()) "No expense matches \"${view.query.trim()}\""
                                else "Nothing here under this filter",
                                color = AppColors.OnSurface,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            TextButton(onClick = { view = ExpenseView() }) {
                                Text("Clear filters", color = AppColors.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                items(visibleExpenses, key = { it.id }) { expense ->
                    val status = Payments.statusOf(expense)
                    val overdue = Payments.isOverdue(expense)

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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The receipt is the fastest thing to recognise a row by, so
                            // it leads rather than hiding behind the edit screen.
                            expense.receiptPath?.let { path ->
                                ReceiptImage(path = path, modifier = Modifier.size(40.dp))
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(expense.title, color = AppColors.OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(
                                    buildString {
                                        append(expense.category)
                                        if (expense.vendor.isNotBlank()) append(" · ${expense.vendor}")
                                        append(" · ${expense.dateCreated.formatDate()}")
                                    },
                                    color = AppColors.Secondary,
                                    fontSize = 12.sp
                                )
                                if (status != PaymentStatus.PAID) {
                                    Text(
                                        buildString {
                                            append("${expense.outstanding.formatMoney(money)} owing")
                                            expense.dueDate?.let {
                                                append(if (overdue) " · was due ${it.formatDate()}" else " · due ${it.formatDate()}")
                                            }
                                        },
                                        color = if (overdue) AppColors.Danger else AppColors.Warning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (expense.notes.isNotBlank()) {
                                    Text(expense.notes, color = AppColors.Secondary.copy(alpha = 0.7f), fontSize = 11.sp)
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    expense.amount.formatMoney(money),
                                    color = AppColors.OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Settling a vendor is the most repeated action once
                                    // bookings start closing out, and routing it through
                                    // the edit form would make it four taps and a save.
                                    if (status != PaymentStatus.PAID) {
                                        TextButton(
                                            onClick = { eventViewModel.setPaymentStatus(expense, PaymentStatus.PAID) },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Text("Mark paid", color = AppColors.Black, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
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
            Icon(Icons.Default.Add, contentDescription = stringResource(words.addEntry))
        }
    }
}

/** A compact filter chip. Smaller than the form's, because these come a dozen to a row. */
@Composable
private fun SmallChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val accent = if (danger) AppColors.Danger else AppColors.Black
    Box(
        Modifier
            .background(
                if (selected) accent else AppColors.Surface,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = when {
                selected -> Color.White
                danger -> AppColors.Danger
                else -> AppColors.Secondary
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun SortMenu(current: ExpenseSort, onPick: (ExpenseSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.Sort, contentDescription = null, tint = AppColors.Secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.label, color = AppColors.Secondary, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sort.label,
                            fontSize = 14.sp,
                            fontWeight = if (sort == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(sort)
                    }
                )
            }
        }
    }
}

/** A label over a figure, for the funding card's three-across row. */
@Composable
private fun MiniFigure(
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

/**
 * Money in. Deliberately fewer fields than the expense form — a contribution is who,
 * how much, and whether it has actually arrived, and asking for anything else would make
 * recording a deposit slower than recording the spend it pays for.
 */
@Composable
private fun AddContributionDialog(
    /** The budget's currency: a contribution to a dollar goal is typed in dollars. */
    money: BudgetMoney,
    onDismiss: () -> Unit,
    onAdd: (source: String, amount: Double, received: Boolean) -> Unit
) {
    var source by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var received by remember { mutableStateOf(true) }
    val amountValue = amount.parseAmount(money) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Add Funding", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("From") },
                    placeholder = { Text("e.g. Client deposit") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount (${ActiveCurrency.value.symbol})") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Already received", color = AppColors.OnSurface, fontSize = 14.sp)
                        Text(
                            if (received) "Counts towards cash in hand" else "Recorded as a promise only",
                            color = AppColors.Secondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = received,
                        onCheckedChange = { received = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.Black,
                            uncheckedThumbColor = AppColors.Secondary,
                            uncheckedTrackColor = AppColors.Border
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(source.trim(), amountValue, received) },
                enabled = source.isNotBlank() && amountValue > 0
            ) { Text("Add", color = AppColors.Black, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.Secondary) } }
    )
}

/**
 * A cost that comes back.
 *
 * The start date defaults to today, and [com.el.sapiospend.domain.recurring.RecurringExpenses.firstDueDate]
 * pushes it forward if it is already past — so a rule set up mid-month cannot
 * retroactively invent the payments that went out before it existed.
 */
@Composable
private fun AddRecurringDialog(
    /** The budget's currency — a rule charges in what the budget is kept in. */
    money: BudgetMoney,
    categories: List<String>,
    eventEnd: Long?,
    onDismiss: () -> Unit,
    onAdd: (title: String, category: String, amount: Double, recurrence: Recurrence, startDate: Long, vendor: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(Recurrence.DEFAULT) }
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPicker by remember { mutableStateOf(false) }
    val options = remember(categories) { (categories + listOf("Others")).filter { it.isNotBlank() }.distinct() }
    var category by remember(options) { mutableStateOf(options.first()) }
    val amountValue = amount.parseAmount(money) ?: 0.0

    if (showPicker) {
        DayCalendarDialog(
            initialDay = startDate,
            title = "First charge",
            onDismiss = { showPicker = false },
            onConfirm = { day ->
                startDate = DateUtils.instantOnDay(day)
                showPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text("Recurring Expense", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What") },
                    placeholder = { Text("e.g. Venue hire") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount each time (${ActiveCurrency.value.symbol})") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How often", color = AppColors.Secondary, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Recurrence.entries.forEach { option ->
                            SmallChip(
                                label = option.label,
                                selected = recurrence == option,
                                onClick = { recurrence = option }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category", color = AppColors.Secondary, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { option ->
                            SmallChip(
                                label = option,
                                selected = category == option,
                                onClick = { category = option }
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.BG, RoundedCornerShape(12.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("First charge ${startDate.formatDate()}", color = AppColors.OnSurface, fontSize = 13.sp)
                    Icon(Icons.Default.CalendarToday, contentDescription = "Change first charge", tint = AppColors.Secondary, modifier = Modifier.size(16.dp))
                }

                Text(
                    eventEnd?.let { stringResource(R.string.detail_recurring_ends, it.formatDate()) }
                        ?: "Runs until you pause it.",
                    color = AppColors.Border,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title.trim(), category, amountValue, recurrence, startDate, vendor.trim()) },
                enabled = title.isNotBlank() && amountValue > 0
            ) { Text("Add", color = AppColors.Black, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.Secondary) } }
    )
}
