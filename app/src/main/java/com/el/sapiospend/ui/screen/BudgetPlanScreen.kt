package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import com.el.sapiospend.domain.plan.BudgetPlanEditor
import com.el.sapiospend.domain.template.CustomCategoryInput
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.util.formatNaira
import kotlin.math.abs

/**
 * Where the *planned* half of a budget gets written.
 *
 * Until this screen the plan could only be set at the moment an event was created, from
 * a template or a one-shot custom list, and never again — so an event created in a hurry
 * had a total and nothing to hold it against, and every breakdown afterwards could only
 * report what had already been spent. Planned-vs-actual needs a plan the user can change
 * their mind about: budgets are revised far more often than they are written.
 *
 * The screen is deliberately editable in both directions. Adding a category is obvious;
 * being able to empty one out and have it disappear from the plan is what makes this an
 * editor rather than an append-only list.
 */
@Composable
fun BudgetPlanScreen(
    eventId: String,
    onBack: () -> Unit = {},
    eventViewModel: EventViewModel
) {
    val events by eventViewModel.events.collectAsState()
    val allExpenses by eventViewModel.allExpenses.collectAsState()
    val event = events.find { it.id == eventId }

    // Null until the stored plan has been read. Rendering the editor before then would
    // show empty rows for an event that has a plan, and saving those would wipe it.
    var rows by remember(eventId) { mutableStateOf<List<CustomCategoryInput>?>(null) }
    LaunchedEffect(eventId) {
        rows = BudgetPlanEditor.rowsFrom(eventViewModel.plannedLinesFor(eventId))
            .ifEmpty { List(3) { CustomCategoryInput() } }
    }

    val currentRows = rows
    if (event == null || currentRows == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.BG),
            contentAlignment = Alignment.Center
        ) {
            if (event == null) Text("Event not found", color = AppColors.Secondary)
        }
        return
    }

    val spentCategories = remember(allExpenses, eventId) {
        allExpenses.filter { it.eventId == eventId }.map { it.category }
    }
    val suggestions = BudgetPlanEditor.unplannedCategories(currentRows, spentCategories)

    val planned = BudgetPlanEditor.plannedTotal(currentRows)
    val unallocated = event.budget - planned
    val overAllocated = unallocated < 0
    val allocatedFraction = if (event.budget > 0) (planned / event.budget).toFloat().coerceIn(0f, 1f) else 0f

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
        Modifier
            .fillMaxSize()
            .background(AppColors.BG)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.Secondary)
            }
            Spacer(Modifier.width(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Planned Budget",
                    color = AppColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text("${event.name} · ${event.budget.formatNaira()}", color = AppColors.Secondary, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Black),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlanStat("Budget", event.budget.formatNaira(), Color.White)
                    PlanStat("Planned", planned.formatNaira(), Color.White)
                    PlanStat(
                        if (overAllocated) "Over by" else "Unallocated",
                        abs(unallocated).formatNaira(),
                        if (overAllocated) Color(0xFFFF6B6B) else Color(0xFF6EE7B7)
                    )
                }
                LinearProgressIndicator(
                    progress = { allocatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (overAllocated) Color(0xFFFF6B6B) else Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Text(
                    // Planning past the budget is allowed and only flagged: it is often
                    // the first honest draft of a budget, and the point of writing the
                    // plan down is to find out that it does not fit.
                    if (overAllocated) "Your categories add up to more than the budget"
                    else "${(allocatedFraction * 100).toInt()}% of the budget is assigned to a category",
                    color = if (overAllocated) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ALREADY SPENT ON, NOT PLANNED",
                    color = AppColors.Secondary,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                // The categories the receipts have already named are the ones most
                // likely to belong in the plan, so they are one tap rather than a
                // retype — with the amount left blank, because what was spent is not
                // an argument for what should have been planned.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.take(8).forEach { category ->
                        AssistChip(
                            onClick = {
                                rows = currentRows.replaceFirstBlankOrAppend(
                                    CustomCategoryInput(name = category)
                                )
                            },
                            label = { Text(category, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = AppColors.Surface,
                                labelColor = AppColors.OnSurface,
                                leadingIconContentColor = AppColors.Secondary
                            ),
                            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = AppColors.Border)
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CATEGORIES", color = AppColors.Secondary, fontSize = 11.sp, letterSpacing = 0.5.sp)
            Text(
                "Set what you intend to spend on each. Clear a row to drop it from the plan.",
                color = AppColors.Border,
                fontSize = 12.sp
            )

            currentRows.forEach { row ->
                // Keyed on the row's own id — which is also the id of the budget line it
                // came from — so removing a row cannot shuffle the text belonging to the
                // rows below it.
                key(row.id) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { value ->
                                rows = currentRows.map { if (it.id == row.id) it.copy(name = value) else it }
                            },
                            placeholder = { Text("Category", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() || it == '.' }) {
                                    rows = currentRows.map { if (it.id == row.id) it.copy(amount = value) else it }
                                }
                            },
                            placeholder = { Text("₦", fontSize = 13.sp) },
                            modifier = Modifier.width(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                        IconButton(
                            // One row always survives, so there is somewhere to type
                            // without hunting for the add button first.
                            onClick = { if (currentRows.size > 1) rows = currentRows.filterNot { it.id == row.id } },
                            enabled = currentRows.size > 1,
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
                onClick = { rows = currentRows + CustomCategoryInput() },
                enabled = currentRows.size < BudgetPlanEditor.MAX_LINES
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AppColors.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add category", color = AppColors.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Button(
            onClick = {
                eventViewModel.savePlan(eventId, currentRows)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Black, contentColor = Color.White)
        ) {
            Text("Save plan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Drops a suggested category into the first row nobody has typed in yet, adding a row
 * only when there is none to spare — so tapping three chips fills the three blank rows
 * the screen opens with instead of leaving them stranded below the new ones.
 */
private fun List<CustomCategoryInput>.replaceFirstBlankOrAppend(
    row: CustomCategoryInput
): List<CustomCategoryInput> {
    val blankIndex = indexOfFirst { it.name.isBlank() && it.amount.isBlank() }
    return if (blankIndex >= 0) {
        // Keeps the blank row's id, so the field the user is about to type in does not
        // lose focus to a newly keyed composable.
        mapIndexed { index, existing ->
            if (index == blankIndex) existing.copy(name = row.name) else existing
        }
    } else {
        this + row
    }
}

@Composable
private fun PlanStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}
