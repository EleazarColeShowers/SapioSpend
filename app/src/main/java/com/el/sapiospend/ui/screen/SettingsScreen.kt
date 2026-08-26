package com.el.sapiospend.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.settings.AppCurrency
import com.el.sapiospend.ui.theme.AppColors
import com.el.sapiospend.util.formatMoney

/** A figure with enough digits to show what the grouping and symbol actually look like. */
private const val PREVIEW_AMOUNT = 1_250_000.0

@Composable
fun SettingsScreen(
    currency: AppCurrency,
    onCurrencyChange: (AppCurrency) -> Unit,
    onBack: () -> Unit = {}
) {
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
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppColors.Secondary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Settings",
                        color = AppColors.OnSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "CURRENCY",
                        color = AppColors.Secondary,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    // Said plainly and up front, because the alternative reading —
                    // that switching currency converts the money — would have somebody
                    // believe their ₦2m wedding budget just became $2m.
                    Text(
                        "Changes how amounts are labelled. Your figures stay exactly as you entered them — nothing is converted.",
                        color = AppColors.Secondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Preview",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            PREVIEW_AMOUNT.formatMoney(currency),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
            }

            items(AppCurrency.entries, key = { it.code }) { option ->
                val selected = option == currency
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCurrencyChange(option) },
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The symbol is the thing the user is actually choosing, so it
                        // gets the visual weight rather than the three-letter code.
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(AppColors.BG, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                option.symbol,
                                color = AppColors.OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                option.displayName,
                                color = AppColors.OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(option.code, color = AppColors.Secondary, fontSize = 12.sp)
                        }

                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = AppColors.Success,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
