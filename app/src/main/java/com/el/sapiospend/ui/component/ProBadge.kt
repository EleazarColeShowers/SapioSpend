package com.el.sapiospend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.ui.theme.AppColors

/** Marks a locked feature. Shown only to users who don't already have it. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(AppColors.Warning.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("PRO", color = AppColors.Warning, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
