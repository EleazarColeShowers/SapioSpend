package com.el.sapiospend.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.BuildConfig
import com.el.sapiospend.R
import com.el.sapiospend.billing.FreePlanLimits
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.ProFeature
import com.el.sapiospend.ui.theme.AppColors

/** What prompted the paywall, so the sheet can lead with the relevant line. */
enum class PaywallTrigger(@get:StringRes val headline: Int, @get:StringRes val subhead: Int) {
    EVENT_LIMIT(R.string.paywall_limit_headline, R.string.paywall_limit_subhead),
    TEMPLATES(R.string.paywall_templates_headline, R.string.paywall_templates_subhead),
    ANALYTICS(R.string.paywall_insights_headline, R.string.paywall_insights_subhead),
    EXPORT(R.string.paywall_export_headline, R.string.paywall_export_subhead)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    trigger: PaywallTrigger,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (trigger == PaywallTrigger.EVENT_LIMIT)
                        stringResource(trigger.headline, FreePlanLimits.MAX_ACTIVE_EVENTS)
                    else stringResource(trigger.headline),
                    color = AppColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(stringResource(trigger.subhead), color = AppColors.Secondary, fontSize = 14.sp)
            }

            HorizontalDivider(color = AppColors.Border)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProFeature.entries.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = AppColors.Success,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                stringResource(feature.label),
                                color = AppColors.OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(stringResource(feature.blurb), color = AppColors.Secondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = AppColors.Border)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SapioSpend Pro", color = AppColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(Plan.PRO.priceLabel, color = AppColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Google Play requires its own billing library for in-app subscriptions, and
            // that library needs products configured in the Play Console. Until that
            // exists there is nothing honest to charge through, so the release build says
            // so rather than presenting a button that silently does nothing.
            if (BuildConfig.DEBUG) {
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text("Unlock Pro (developer build)", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Debug builds only. Replace with the Play Billing purchase flow before release.",
                    color = AppColors.Secondary,
                    fontSize = 11.sp
                )
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = AppColors.Border,
                        disabledContentColor = AppColors.Secondary
                    )
                ) {
                    Text("Coming soon", fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not now", color = AppColors.Secondary)
            }
        }
    }
}
