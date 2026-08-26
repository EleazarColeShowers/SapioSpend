package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
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
import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.ui.component.DayCalendarDialog
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.DateUtils
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.settings.ActiveCurrency

/**
 * One form for recording an expense and for correcting one.
 *
 * The two are the same form because they are the same decision — what was bought, for
 * how much, against which category, on what day. A separate edit screen would be the
 * same fields written twice, and the pair would drift: the category list would gain a
 * planned category on one screen and not the other, and only one of them would ever get
 * the date field.
 */
@Composable
fun ExpenseFormScreen(
    /** The event this expense belongs to, and the one a new expense is recorded against. */
    eventId: String,
    /**
     * Every event, so an expense recorded against the wrong one can be moved. Left empty
     * when there is nothing to move it to, which hides the picker entirely.
     */
    events: List<EventEntity> = emptyList(),
    /** Budget lines across all events; the category chips come from whichever is selected. */
    budgetLines: List<BudgetLineEntity> = emptyList(),
    /** The expense being corrected, or null when recording a new one. */
    existing: ExpenseEntity? = null,
    onBack: () -> Unit = {},
    onSave: (eventId: String, title: String, category: String, amount: Double, notes: String, date: Long) -> Unit =
        { _, _, _, _, _, _ -> }
) {
    val focusManager = LocalFocusManager.current
    val isEditing = existing != null

    var targetEventId by remember(eventId) { mutableStateOf(eventId) }

    /**
     * Categories the selected event actually budgeted for. They lead the list so spend
     * lands against the plan — a generic "Food" chip recorded against a plan that says
     * "Catering & Drinks" would make every expense look unplanned and leave the whole
     * planned-vs-actual comparison reading as broken.
     */
    fun plannedFor(id: String) = budgetLines.filter { it.eventId == id }.map { it.category }

    val fallbackCategories =
        listOf("Food", "Venue", "Transport", "Decoration", "Entertainment", "Clothing", "Others")

    var selectedCategory by remember(existing, eventId) {
        mutableStateOf(
            existing?.category
                ?: (plannedFor(eventId) + fallbackCategories).first { it.isNotBlank() }
        )
    }

    val categories = remember(budgetLines, targetEventId, existing, selectedCategory) {
        // The selection is appended rather than promoted: it guarantees the chosen chip
        // is on screen after a move to an event that never planned for it, without the
        // list reordering itself under the user's finger as they tap along it.
        (listOfNotNull(existing?.category) + plannedFor(targetEventId) + fallbackCategories + selectedCategory)
            .filter { it.isNotBlank() }
            .distinct()
    }

    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var amount by remember(existing) {
        mutableStateOf(existing?.amount?.let { if (it == kotlin.math.floor(it)) "%.0f".format(it) else it.toString() }.orEmpty())
    }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var date by remember(existing) { mutableLongStateOf(existing?.dateCreated ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DayCalendarDialog(
            initialDay = date,
            title = "Date of expense",
            onDismiss = { showDatePicker = false },
            onConfirm = { day ->
                date = DateUtils.instantOnDay(day)
                showDatePicker = false
            }
        )
    }

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
            // The form grew a date row and can carry a long planned-category list, which
            // together outrun a short screen with the keyboard open.
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
                    if (isEditing) "Edit Expense" else "Add Expense",
                    color = AppColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    if (isEditing) "Correct what you recorded" else "Record a spending item",
                    color = AppColors.Secondary,
                    fontSize = 13.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title") },
                    placeholder = { Text("e.g. Catering service") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount (${ActiveCurrency.value.symbol})") },
                    placeholder = { Text("e.g. 25000") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. vendor name, receipt #") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.BG, RoundedCornerShape(12.dp))
                            .clickable {
                                focusManager.clearFocus()
                                showDatePicker = true
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date.formatDate(), color = AppColors.OnSurface, fontSize = 14.sp)
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Change date",
                            tint = AppColors.Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Offered only when correcting an expense: a new one is always recorded
                // from inside an event, so asking which event it belongs to would be
                // asking a question the user has already answered.
                if (isEditing && events.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Event", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            events.forEach { candidate ->
                                FilterChip(
                                    selected = targetEventId == candidate.id,
                                    onClick = { targetEventId = candidate.id },
                                    label = { Text(candidate.name, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppColors.BG,
                                        labelColor = AppColors.Secondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = targetEventId == candidate.id,
                                        borderColor = AppColors.Border,
                                        selectedBorderColor = AppColors.Black
                                    )
                                )
                            }
                        }
                        if (targetEventId != eventId) {
                            Text(
                                "Moving this expense takes its amount off the old event's total and onto this one.",
                                color = AppColors.Secondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = AppColors.BG,
                                    labelColor = AppColors.Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == category,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.Black
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            targetEventId,
                            title.trim(),
                            selectedCategory,
                            amount.toDoubleOrNull() ?: 0.0,
                            notes.trim(),
                            date
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
                    enabled = title.isNotBlank() && amount.isNotBlank()
                ) {
                    Text(if (isEditing) "Save Changes" else "Save Expense", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
