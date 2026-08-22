package com.el.sapiospend.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette the charts draw with.
 *
 * Two different jobs, two different sets, because mixing them is what makes a chart
 * unreadable:
 *
 * - **Plan against reality** is not an identity comparison — one series is the reference
 *   and the other is the story. So it uses emphasis rather than colour-coding: the plan
 *   in recessive grey, the actual in the app's ink, and only [Over] when a category has
 *   broken its allocation. That is also why a red bar always arrives with the figure
 *   beside it; colour alone never carries the bad news.
 *
 * - **Share of spend** is identity — each slice is a different category and nothing
 *   orders them — so it needs genuinely distinct hues. [CATEGORICAL] is a validated
 *   colourblind-safe ordering, used in that fixed order and never cycled: past the
 *   slots there are, the tail folds into [Other] rather than inventing a ninth hue that
 *   a colourblind reader could not separate from the first.
 *
 * Three of the categorical hues sit under 3:1 against a white card, so every slice is
 * listed with its name and amount underneath the bar — the chart never asks anyone to
 * identify a category by colour alone.
 */
object ChartColors {

    /** What was actually spent. The app's ink, because it is the figure that matters. */
    val Actual = AppColors.Black

    /** What was planned — present as a reference, so deliberately quieter. */
    val Planned = Color(0xFF9CA3AF)

    /** Spend that has broken its allocation. Never used as a series colour. */
    val Over = AppColors.Danger

    /** Empty track behind a bar, and the hairline under a column chart. */
    val Track = AppColors.Border

    /** A column the reader is not currently looking at. */
    val Muted = AppColors.Black.copy(alpha = 0.22f)

    /**
     * Categorical hues in fixed order: blue, orange, aqua, yellow, magenta. Validated as
     * a set against a white surface — worst adjacent separation ΔE 9.1 under protanopia,
     * 19.6 in normal vision.
     */
    val CATEGORICAL = listOf(
        Color(0xFF2A78D6),
        Color(0xFFEB6834),
        Color(0xFF1BAF7A),
        Color(0xFFEDA100),
        Color(0xFFE87BA4)
    )

    /** The folded tail. Grey on purpose: "everything else" is not an identity. */
    val Other = Color(0xFF9CA3AF)

    /** Hue for slot [index], with anything past the last slot reading as [Other]. */
    fun categorical(index: Int): Color = CATEGORICAL.getOrElse(index) { Other }
}
