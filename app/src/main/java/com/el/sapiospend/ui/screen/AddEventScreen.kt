package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.domain.template.BudgetTemplate
import com.el.sapiospend.domain.template.BudgetTemplates
import com.el.sapiospend.domain.template.CategoryAmount
import com.el.sapiospend.domain.template.CustomCategoryInput
import com.el.sapiospend.domain.template.CustomPlan
import com.el.sapiospend.domain.template.EventTypes
import com.el.sapiospend.ui.component.PeriodCalendarDialog
import com.el.sapiospend.ui.component.ProBadge
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.DateUtils
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.util.formatPeriod
import com.el.sapiospend.settings.ActiveCurrency

/**
 * Period shortcuts. A salary earner wants "this month" in one tap; everyone else wants a
 * date range or nothing at all, so the calendar stays behind [PeriodPreset.CUSTOM].
 */
private enum class PeriodPreset(val label: String) {
    NONE("No dates"),
    THIS_MONTH("This month"),
    NEXT_MONTH("Next month"),
    CUSTOM("Custom")
}

/**
 * Event type, then a starting point, then the details.
 *
 * Splitting the form into steps is what lets the template list be short and relevant: by
 * the time templates are shown the type is known, so only that type's templates need to
 * appear. It also gives the custom plan somewhere to live — it is a choice of starting
 * point ("I'll write my own categories"), not a kind of event.
 */
private enum class WizardStep { TYPE, TEMPLATE, DETAILS }

/**
 * Everything the wizard collected. A data class rather than an eight-argument callback,
 * for the same reason [com.el.sapiospend.ui.screen.ExpenseFormResult] is one: the two
 * nullable dates and a nullable count next to each other are indistinguishable
 * positionally, and getting them the wrong way round would be silent.
 */
data class NewEventInput(
    val name: String,
    val budget: Double,
    val eventType: String,
    val template: BudgetTemplate?,
    val customLines: List<CategoryAmount>,
    val startDate: Long?,
    val endDate: Long?,
    val guestCount: Int?
)

@Composable
fun AddEventScreen(
    proUnlocked: Boolean = false,
    onBack: () -> Unit = {},
    onRequirePro: () -> Unit = {},
    onSaveEvent: (NewEventInput) -> Unit = {}
) {
    var step by remember { mutableStateOf(WizardStep.TYPE) }
    var selectedType by remember { mutableStateOf(EventTypes.ALL.first()) }
    var selectedTemplate by remember { mutableStateOf<BudgetTemplate?>(null) }
    var isCustomPlan by remember { mutableStateOf(false) }
    var customCategories by remember { mutableStateOf(CustomPlan.blankRows()) }

    var eventName by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var guests by remember { mutableStateOf("") }
    var periodPreset by remember { mutableStateOf(PeriodPreset.NONE) }
    var periodStart by remember { mutableStateOf<Long?>(null) }
    var periodEnd by remember { mutableStateOf<Long?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val isPersonal = selectedType == EventTypes.PERSONAL

    // A personal budget without a month is just a number that never resets, so picking
    // the type pre-selects the current month. Only when the user hasn't chosen a period
    // themselves — overriding a deliberate choice would be worse than not helping.
    LaunchedEffect(isPersonal) {
        if (isPersonal && periodPreset == PeriodPreset.NONE) {
            periodPreset = PeriodPreset.THIS_MONTH
            DateUtils.monthBounds(0).let { (start, end) ->
                periodStart = start
                periodEnd = end
            }
        }
    }

    val budgetValue = budget.toDoubleOrNull() ?: 0.0
    val canSave = eventName.isNotBlank() && budgetValue > 0

    val templates = remember(selectedType) { BudgetTemplates.forEventType(selectedType) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.OnSurface,
        unfocusedTextColor = AppColors.OnSurface,
        focusedLabelColor = AppColors.Black,
        unfocusedLabelColor = AppColors.Secondary,
        focusedPlaceholderColor = AppColors.Border,
        unfocusedPlaceholderColor = AppColors.Border,
        focusedBorderColor = AppColors.Black,
        unfocusedBorderColor = AppColors.Border,
        cursorColor = AppColors.Black,
        focusedContainerColor = AppColors.Surface,
        unfocusedContainerColor = AppColors.Surface
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BG)
            // A custom plan can run to a dozen category rows, and the template preview is
            // long on its own, so the whole step scrolls rather than clipping the button
            // at the bottom of it.
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val title = when (step) {
            WizardStep.TYPE -> "What are you planning?"
            WizardStep.TEMPLATE -> "Pick a starting point"
            WizardStep.DETAILS -> if (isPersonal) "New Budget" else "New Event"
        }
        val subtitle = when (step) {
            WizardStep.TYPE -> "Step 1 of 3 · Choose a type"
            WizardStep.TEMPLATE -> "Step 2 of 3 · $selectedType"
            WizardStep.DETAILS -> "Step 3 of 3 · $selectedType · ${startingPointLabel(selectedTemplate, isCustomPlan)}"
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                // Back walks the steps before it leaves the screen, so a wrong turn at
                // step three doesn't throw away the two choices before it.
                when (step) {
                    WizardStep.TYPE -> onBack()
                    WizardStep.TEMPLATE -> step = WizardStep.TYPE
                    WizardStep.DETAILS -> step = WizardStep.TEMPLATE
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.Secondary)
            }
            Spacer(Modifier.width(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    color = AppColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(subtitle, color = AppColors.Secondary, fontSize = 13.sp)
            }
        }

        when (step) {

            WizardStep.TYPE -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        EventTypes.ALL.forEachIndexed { index, type ->
                            if (index > 0) HorizontalDivider(color = AppColors.Border.copy(alpha = 0.4f))
                            ChoiceRow(
                                title = type,
                                subtitle = EventTypes.blurbFor(type),
                                selected = selectedType == type,
                                onClick = {
                                    // Templates belong to a type, so changing the type
                                    // invalidates whatever was picked under the old one.
                                    if (type != selectedType) {
                                        selectedType = type
                                        selectedTemplate = null
                                        isCustomPlan = false
                                    }
                                    step = WizardStep.TEMPLATE
                                }
                            )
                        }
                    }
                }
            }

            WizardStep.TEMPLATE -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "TEMPLATES FOR $selectedType".uppercase(),
                        color = AppColors.Secondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    if (!proUnlocked) ProBadge()
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        templates.forEachIndexed { index, template ->
                            if (index > 0) HorizontalDivider(color = AppColors.Border.copy(alpha = 0.4f))
                            ChoiceRow(
                                title = template.name,
                                subtitle = template.description,
                                selected = selectedTemplate?.id == template.id,
                                // Free users can still tap: the paywall is more useful
                                // than a row that does nothing.
                                onClick = {
                                    if (proUnlocked) {
                                        selectedTemplate = template
                                        isCustomPlan = false
                                        step = WizardStep.DETAILS
                                    } else {
                                        onRequirePro()
                                    }
                                }
                            )
                        }
                        if (templates.isEmpty()) {
                            Text(
                                "No ready-made templates for this type yet — build your own below.",
                                color = AppColors.Secondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                Text("OR", color = AppColors.Border, fontSize = 11.sp, letterSpacing = 1.sp)

                // Deliberately its own card rather than one more row in the list above:
                // a custom plan is not another template, it is the escape hatch for
                // everything the catalogue will never cover.
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    ChoiceRow(
                        title = "Custom Plan",
                        subtitle = "Write your own categories and amounts — free to use",
                        selected = isCustomPlan,
                        onClick = {
                            isCustomPlan = true
                            selectedTemplate = null
                            step = WizardStep.DETAILS
                        }
                    )
                }
            }

            WizardStep.DETAILS -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = eventName,
                            onValueChange = { eventName = it },
                            label = { Text(if (isPersonal) "Budget Name" else "Event Name") },
                            placeholder = { Text(namePlaceholder(selectedType, isCustomPlan)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )

                        OutlinedTextField(
                            value = budget,
                            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) budget = v },
                            label = { Text(if (isPersonal) "Take-Home Pay (${ActiveCurrency.value.symbol})" else "Total Budget (${ActiveCurrency.value.symbol})") },
                            placeholder = { Text("e.g. 100000") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            isError = budget.isNotBlank() && budgetValue <= 0,
                            supportingText = if (budget.isNotBlank() && budgetValue <= 0) {
                                { Text("Budget must be greater than zero", color = AppColors.Danger, fontSize = 11.sp) }
                            } else null
                        )

                        // Only for an event: a salary budget has no guests, and a
                        // cost-per-head figure on one would be nonsense.
                        if (!isPersonal) {
                            OutlinedTextField(
                                value = guests,
                                onValueChange = { v -> if (v.all { it.isDigit() }) guests = v },
                                label = { Text("Guests (optional)") },
                                placeholder = { Text("e.g. 250") },
                                supportingText = {
                                    Text(
                                        guests.toIntOrNull()?.takeIf { it > 0 && budgetValue > 0 }
                                            ?.let { "${(budgetValue / it).formatMoney()} per guest" }
                                            ?: "Unlocks cost per head on the analytics screen",
                                        color = AppColors.Secondary,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (isPersonal) "Budget Month" else "Budget Period",
                                color = AppColors.Secondary,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PeriodPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = periodPreset == preset,
                                        onClick = {
                                            periodPreset = preset
                                            when (preset) {
                                                PeriodPreset.NONE -> {
                                                    periodStart = null
                                                    periodEnd = null
                                                }
                                                PeriodPreset.THIS_MONTH -> DateUtils.monthBounds(0).let { (s, e) ->
                                                    periodStart = s
                                                    periodEnd = e
                                                }
                                                PeriodPreset.NEXT_MONTH -> DateUtils.monthBounds(1).let { (s, e) ->
                                                    periodStart = s
                                                    periodEnd = e
                                                }
                                                PeriodPreset.CUSTOM -> showRangePicker = true
                                            }
                                        },
                                        label = { Text(preset.label, fontSize = 13.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AppColors.Black,
                                            selectedLabelColor = Color.White,
                                            containerColor = AppColors.BG,
                                            labelColor = AppColors.Secondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = periodPreset == preset,
                                            borderColor = AppColors.Border,
                                            selectedBorderColor = AppColors.Black
                                        )
                                    )
                                }
                            }

                            formatPeriod(periodStart, periodEnd)?.let { label ->
                                Text(label, color = AppColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            } ?: Text(
                                // Says what is lost rather than just what is missing: the
                                // pacing figures are the whole reason to set dates.
                                "Without dates there is no daily allowance or days-left figure",
                                color = AppColors.Border,
                                fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(color = AppColors.Border.copy(alpha = 0.4f))

                        if (isCustomPlan) {
                            CustomPlanEditor(
                                categories = customCategories,
                                budgetValue = budgetValue,
                                fieldColors = fieldColors,
                                onChange = { customCategories = it },
                                onUseTotalAsBudget = { total ->
                                    budget = "%.0f".format(total)
                                }
                            )
                        } else {
                            TemplatePreview(template = selectedTemplate, budgetValue = budgetValue)
                        }

                        Button(
                            onClick = {
                                onSaveEvent(
                                    NewEventInput(
                                        name = eventName.trim(),
                                        budget = budgetValue,
                                        eventType = selectedType,
                                        template = selectedTemplate,
                                        customLines = if (isCustomPlan) CustomPlan.linesOf(customCategories) else emptyList(),
                                        startDate = periodStart,
                                        endDate = periodEnd,
                                        // Blank stays null rather than becoming zero: an
                                        // uncounted event and an event for nobody are
                                        // different, and only one of them has a cost per
                                        // head worth showing.
                                        guestCount = guests.toIntOrNull()?.takeIf { it > 0 }
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Black,
                                contentColor = Color.White,
                                disabledContainerColor = AppColors.Border,
                                disabledContentColor = AppColors.Secondary
                            ),
                            enabled = canSave
                        ) {
                            Text(if (isPersonal) "Create Budget" else "Create Event", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        PeriodCalendarDialog(
            initialStart = periodStart,
            initialEnd = periodEnd,
            onDismiss = {
                showRangePicker = false
                // Backing out of the calendar without a range would otherwise leave the
                // Custom chip selected over whatever dates were there before.
                if (periodStart == null || periodEnd == null) periodPreset = PeriodPreset.NONE
            },
            onConfirm = { start, end ->
                periodStart = start
                periodEnd = end
                periodPreset = PeriodPreset.CUSTOM
                showRangePicker = false
            }
        )
    }
}

/** What step three says the plan is based on. */
private fun startingPointLabel(template: BudgetTemplate?, isCustomPlan: Boolean): String = when {
    isCustomPlan -> "Custom Plan"
    template != null -> template.name
    else -> "No template"
}

private fun namePlaceholder(eventType: String, isCustomPlan: Boolean): String = when {
    isCustomPlan && eventType == EventTypes.PERSONAL -> "e.g. Setting Up My New Apartment"
    isCustomPlan -> "e.g. What you're planning for"
    eventType == EventTypes.PERSONAL -> "e.g. August Salary"
    eventType == "Wedding" -> "e.g. Tolu & Ada's Wedding"
    else -> "e.g. Eleazar's Birthday"
}

/** One tappable option in the type and template lists. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.Secondary, fontSize = 12.sp)
        }
        Icon(
            if (selected) Icons.Default.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (selected) AppColors.Success else AppColors.Border,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The breakdown a template would write, priced against whatever budget has been typed. */
@Composable
private fun TemplatePreview(template: BudgetTemplate?, budgetValue: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("STARTING BREAKDOWN", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)

        if (template == null) {
            Text(
                "No template — you can still add expenses and track the total.",
                color = AppColors.Border,
                fontSize = 12.sp
            )
            return@Column
        }

        Text(template.description, color = AppColors.Secondary, fontSize = 12.sp)

        if (budgetValue <= 0) {
            Text("Enter a budget to preview the breakdown", color = AppColors.Border, fontSize = 12.sp)
            return@Column
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            template.allocate(budgetValue).forEach { allocation ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(allocation.category, color = AppColors.Secondary, fontSize = 12.sp)
                    Text(
                        allocation.amount.formatMoney(),
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
 * The custom plan: as many named categories as the user wants, each with its own figure,
 * totalled against the budget as they type.
 *
 * The running total is the reason this exists. Anybody can write a list of things to buy;
 * what they cannot do on paper is see, on every keystroke, that the list has quietly
 * outgrown what they have.
 */
@Composable
private fun CustomPlanEditor(
    categories: List<CustomCategoryInput>,
    budgetValue: Double,
    fieldColors: TextFieldColors,
    onChange: (List<CustomCategoryInput>) -> Unit,
    onUseTotalAsBudget: (Double) -> Unit
) {
    val planned = CustomPlan.plannedTotal(categories)
    val unallocated = budgetValue - planned
    val overBudget = unallocated < 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("YOUR CATEGORIES", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
        Text(
            "Name what you're spending on and set an amount for each.",
            color = AppColors.Border,
            fontSize = 12.sp
        )

        categories.forEach { category ->
            // Keyed on the row's own id so removing a row cannot shuffle the text that
            // belongs to the rows below it.
            key(category.id) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = category.name,
                        onValueChange = { value ->
                            onChange(categories.map { if (it.id == category.id) it.copy(name = value) else it })
                        },
                        placeholder = { Text("Category", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = category.amount,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() || it == '.' }) {
                                onChange(categories.map { if (it.id == category.id) it.copy(amount = value) else it })
                            }
                        },
                        placeholder = { Text(ActiveCurrency.value.symbol, fontSize = 13.sp) },
                        modifier = Modifier.width(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    IconButton(
                        // The last row is never removed, so there is always somewhere to
                        // type without having to find the add button first.
                        onClick = { if (categories.size > 1) onChange(categories.filterNot { it.id == category.id }) },
                        enabled = categories.size > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove category",
                            tint = AppColors.Border,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = { onChange(categories + CustomCategoryInput()) },
            enabled = categories.size < CustomPlan.MAX_CATEGORIES
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add category", color = AppColors.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(AppColors.BG, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Allocated", color = AppColors.Secondary, fontSize = 13.sp)
                Text(planned.formatMoney(), color = AppColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (overBudget) "Over budget by" else "Remaining", color = AppColors.Secondary, fontSize = 13.sp)
                Text(
                    kotlin.math.abs(unallocated).formatMoney(),
                    color = if (overBudget) AppColors.Danger else AppColors.Success,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when {
                budgetValue <= 0 && planned > 0 -> TextButton(
                    onClick = { onUseTotalAsBudget(planned) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "Use ${planned.formatMoney()} as the total budget",
                        color = AppColors.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Overshooting is allowed — the event still saves. Saying so beats a
                // blocked button the user cannot explain.
                overBudget -> Text(
                    "Your categories add up to more than the budget. You can still save it.",
                    color = AppColors.Danger,
                    fontSize = 11.sp
                )
            }
        }
    }
}
