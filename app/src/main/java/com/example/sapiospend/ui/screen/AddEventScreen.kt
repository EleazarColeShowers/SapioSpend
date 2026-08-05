package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.sapiospend.domain.template.BudgetTemplate
import com.example.sapiospend.domain.template.BudgetTemplates
import com.example.sapiospend.ui.component.ProBadge
import com.example.sapiospend.ui.theme.AppColors
import com.example.sapiospend.util.formatNaira

@Composable
fun AddEventScreen(
    isPro: Boolean = false,
    onBack: () -> Unit = {},
    onRequirePro: () -> Unit = {},
    onSaveEvent: (name: String, budget: Double, eventType: String, template: BudgetTemplate?) -> Unit =
        { _, _, _, _ -> }
) {
    var eventName by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Birthday") }
    var selectedTemplate by remember { mutableStateOf<BudgetTemplate?>(null) }

    val focusManager = LocalFocusManager.current
    val eventTypes = listOf("Birthday", "Wedding", "Social Gathering", "Corporate", "Other")

    val budgetValue = budget.toDoubleOrNull() ?: 0.0
    val canSave = eventName.isNotBlank() && budgetValue > 0

    // Templates matching the chosen type come first, so the relevant ones are visible
    // without scrolling the row.
    val templates = remember(selectedType) { BudgetTemplates.suggestedFor(selectedType) }

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
            // The template picker and its preview push the form past a small screen's
            // height, so the whole thing scrolls rather than clipping the save button.
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
                Text("New Event", color = AppColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Fill in the details below", color = AppColors.Secondary, fontSize = 13.sp)
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
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Name") },
                    placeholder = { Text("e.g. Eleazar's Birthday") },
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
                    label = { Text("Total Budget (₦)") },
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Event Type", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        eventTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = AppColors.BG,
                                    labelColor = AppColors.Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedType == type,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.Black
                                )
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Start From a Template", color = AppColors.Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
                        if (!isPro) ProBadge()
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTemplate == null,
                            onClick = { selectedTemplate = null },
                            label = { Text("None", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Black,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.BG,
                                labelColor = AppColors.Secondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedTemplate == null,
                                borderColor = AppColors.Border,
                                selectedBorderColor = AppColors.Black
                            )
                        )
                        templates.forEach { template ->
                            FilterChip(
                                selected = selectedTemplate?.id == template.id,
                                // Free users can still tap: the paywall is more useful
                                // than a chip that does nothing.
                                onClick = { if (isPro) selectedTemplate = template else onRequirePro() },
                                label = { Text(template.name, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = AppColors.BG,
                                    labelColor = AppColors.Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedTemplate?.id == template.id,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.Black
                                )
                            )
                        }
                    }

                    selectedTemplate?.let { template ->
                        Text(template.description, color = AppColors.Secondary, fontSize = 12.sp)

                        if (budgetValue > 0) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                template.allocate(budgetValue).forEach { allocation ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(allocation.category, color = AppColors.Secondary, fontSize = 12.sp)
                                        Text(
                                            allocation.amount.formatNaira(),
                                            color = AppColors.OnSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Enter a budget to preview the breakdown",
                                color = AppColors.Border,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveEvent(eventName.trim(), budgetValue, selectedType, selectedTemplate)
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
                    Text("Create Event", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
