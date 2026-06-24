package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG = Color(0xFFF5F5F5)
private val Surface = Color(0xFFFFFFFF)
private val OnSurface = Color(0xFF111111)
private val Secondary = Color(0xFF6B7280)
private val Border = Color(0xFFE5E7EB)
private val Black = Color(0xFF111111)

@Composable
fun AddExpenseScreen(
    onBack: () -> Unit = {},
    onSaveExpense: (title: String, category: String, amount: Double) -> Unit = { _, _, _ -> }
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }

    val categories = listOf("Food", "Venue", "Transport", "Decoration", "Entertainment", "Clothing", "Others")

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = OnSurface,
        unfocusedTextColor = OnSurface,
        focusedLabelColor = Black,
        unfocusedLabelColor = Secondary,
        focusedPlaceholderColor = Border,
        unfocusedPlaceholderColor = Border,
        focusedBorderColor = Black,
        unfocusedBorderColor = Border,
        cursorColor = Black,
        focusedContainerColor = Surface,
        unfocusedContainerColor = Surface
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Secondary)
            }
            Spacer(Modifier.width(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Add Expense", color = OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Record a spending item", color = Secondary, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
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
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) amount = v },
                    label = { Text("Amount (₦)") },
                    placeholder = { Text("e.g. 25000") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", color = Secondary, fontSize = 12.sp, letterSpacing = 0.5.sp)
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
                                    selectedContainerColor = Black,
                                    selectedLabelColor = Color.White,
                                    containerColor = BG,
                                    labelColor = Secondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == category,
                                    borderColor = Border,
                                    selectedBorderColor = Black
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveExpense(title.trim(), selectedCategory, amount.toDoubleOrNull() ?: 0.0)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Black,
                        contentColor = Color.White,
                        disabledContainerColor = Border,
                        disabledContentColor = Secondary
                    ),
                    enabled = title.isNotBlank() && amount.isNotBlank()
                ) {
                    Text("Save Expense", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
