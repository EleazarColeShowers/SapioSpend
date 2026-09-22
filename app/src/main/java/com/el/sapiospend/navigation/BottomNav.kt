package com.el.sapiospend.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.R
import com.el.sapiospend.ui.theme.AppColors

/**
 * The app's three top-level places.
 *
 * Insights and Settings used to be unlabelled icons in the corner of Home, which made
 * them invisible from every other screen and left the chart icon a guess — a guess that
 * led to a paywall. As tabs they are named, and they are reachable from wherever the
 * user happens to be.
 */
enum class BottomTab(
    val route: String,
    @get:StringRes val label: Int,
    val icon: ImageVector
) {
    BUDGETS(Routes.Home.route, R.string.nav_budgets, Icons.Default.AccountBalanceWallet),
    INSIGHTS(Routes.Analytics.route, R.string.nav_insights, Icons.Default.Insights),
    SETTINGS(Routes.Settings.route, R.string.nav_settings, Icons.Default.Settings);

    companion object {
        /** The tab owning [route], or null on a pushed screen that shows no bar at all. */
        fun forRoute(route: String?): BottomTab? = entries.find { it.route == route }
    }
}

@Composable
fun AppBottomBar(current: BottomTab?, onSelect: (BottomTab) -> Unit) {
    // A hairline rather than the default tonal elevation: every surface in this app is
    // flat, and a shadowed bar would be the one raised thing on screen. The Column is
    // what keeps the two as one measurable - Scaffold's bottomBar slot stacks siblings
    // on top of each other rather than laying them out.
    Column {
        HorizontalDivider(color = AppColors.Border)
        NavigationBar(containerColor = AppColors.Surface, tonalElevation = 0.dp) {
            BottomTab.entries.forEach { tab ->
                val selected = tab == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            stringResource(tab.label),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = AppColors.OnSurface,
                        indicatorColor = AppColors.Black,
                        unselectedIconColor = AppColors.Secondary,
                        unselectedTextColor = AppColors.Secondary
                    )
                )
            }
        }
    }
}
