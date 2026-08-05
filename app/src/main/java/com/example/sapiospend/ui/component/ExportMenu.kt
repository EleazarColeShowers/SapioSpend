package com.example.sapiospend.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sapiospend.export.ExportFormat
import com.example.sapiospend.ui.theme.AppColors


@Composable
fun ExportMenu(
    isPro: Boolean,
    isExporting: Boolean,
    onExport: (ExportFormat) -> Unit,
    onRequirePro: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = AppColors.Secondary
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { if (isPro) expanded = true else onRequirePro() },
            enabled = !isExporting
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = tint)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExportFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text("Export as ${format.label}", fontSize = 14.sp) },
                    onClick = {
                        expanded = false
                        onExport(format)
                    }
                )
            }
        }
    }
}
