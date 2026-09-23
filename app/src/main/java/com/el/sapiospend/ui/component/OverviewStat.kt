package com.el.sapiospend.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One figure in a dark summary card's row of three.
 *
 * Give each one a `weight(1f)` so the row is split into equal shares: the amount then
 * shrinks to fit its share instead of wrapping. A long total — a large budget, or a
 * currency whose symbol is three letters wide — would otherwise push the figure onto a
 * second line and leave the three labels sitting at different heights.
 */
@Composable
fun OverviewStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    /**
     * The same figure in the currency the rest of the app is read in, for a budget kept
     * in another one. Null when there is nothing to convert, which is the common case —
     * an empty line here would put a gap under every figure in the app to serve the few
     * that need it.
     */
    converted: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            color = color,
            fontWeight = FontWeight.Bold,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp),
            maxLines = 1,
            softWrap = false,
            // A figure too long even at the smallest step is truncated rather than
            // clipped, so it never ends mid-digit and reads as a smaller number.
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        // Below the label, not above it: the converted figure is an orientation, and
        // putting it between the amount and its label would read as a second amount.
        converted?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
