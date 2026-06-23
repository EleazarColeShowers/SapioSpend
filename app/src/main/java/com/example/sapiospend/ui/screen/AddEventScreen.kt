package com.example.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sapiospend.data.model.Event

private val Navy = Color(0xFF0F172A)
private val NavyLight = Color(0xFF1E293B)
private val Slate = Color(0xFF334155)
private val SlateText = Color(0xFF94A3B8)
private val Accent = Color(0xFF6366F1)

@Composable
fun AddEventScreen(
    onBack: () -> Unit = {},
    onSaveEvent: (Event) -> Unit = {}
) {
    var eventName by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }

    val categories = listOf("General", "Food", "Venue", "Transport", "Decoration", "Others")

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Accent,
        unfocusedLabelColor = SlateText,
        focusedPlaceholderColor = Slate,
        unfocusedPlaceholderColor = Slate,
        focusedBorderColor = Accent,
        unfocusedBorderColor = Slate,
        cursorColor = Accent
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SlateText
                )
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "New Event",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Fill in the details below", color = SlateText, fontSize = 13.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(NavyLight),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Name") },
                    placeholder = { Text("e.g Birthday Party") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Total Budget (₦)") },
                    placeholder = { Text("e.g 100000") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", color = SlateText, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White,
                                    containerColor = NavyLight,
                                    labelColor = SlateText
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == category,
                                    borderColor = Slate,
                                    selectedBorderColor = Accent
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveEvent(Event(name = eventName, budget = budget.toDoubleOrNull() ?: 0.0))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = eventName.isNotBlank() && budget.isNotBlank()
                ) {
                    Text("Create Event", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}