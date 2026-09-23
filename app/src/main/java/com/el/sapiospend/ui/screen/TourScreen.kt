package com.el.sapiospend.ui.screen

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.el.sapiospend.R
import com.el.sapiospend.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * The four screens a first run opens on.
 *
 * It exists because of what the empty state could not say. "No budgets yet — tap + to
 * create your first budget" is accurate and sells nothing: the best thing in the app is
 * a ready-made breakdown for the kind of thing you are planning, and it sits behind that
 * button where a first-time user has no reason to look for it.
 *
 * Four screens, not seven, and skippable from the first — a tutorial nobody can escape
 * is the other way to lose someone on their first run. It is shown once, and Settings
 * can replay it for anyone who skipped and later wished they had not.
 *
 * Each page carries a small mock of a real surface rather than an illustration. There is
 * no artwork in this project, and a diagram of the actual screen is both cheaper and
 * more honest than a stock image of somebody holding a phone.
 */
private enum class TourPage(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int
) {
    WHAT(R.string.tour_1_title, R.string.tour_1_body),
    TEMPLATES(R.string.tour_2_title, R.string.tour_2_body),
    LOGGING(R.string.tour_3_title, R.string.tour_3_body),
    INSIGHTS(R.string.tour_4_title, R.string.tour_4_body)
}

@Composable
fun TourScreen(onFinish: () -> Unit) {
    val pages = TourPage.entries
    val state = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex

    // Back steps through the tour rather than leaving the app from page four, which is
    // what a NavHost with nothing under it would otherwise do.
    BackHandler(enabled = state.currentPage > 0) {
        scope.launch { state.animateScrollToPage(state.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.BG)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Present from the first page. Somebody who already knows what a budget is
            // should not have to swipe four times to prove it.
            TextButton(onClick = onFinish) {
                Text(stringResource(R.string.tour_skip), color = AppColors.Secondary, fontSize = 14.sp)
            }
        }

        HorizontalPager(
            state = state,
            modifier = Modifier.weight(1f)
        ) { index ->
            val page = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                PageArtwork(page)
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(page.title),
                    color = AppColors.OnSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.body),
                    color = AppColors.Secondary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEachIndexed { index, _ ->
                    val active = index == state.currentPage
                    // The current page's dot stretches rather than just darkening, so
                    // the position is readable without relying on colour alone.
                    val width by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
                    val color by animateColorAsState(
                        if (active) AppColors.Black else AppColors.Border,
                        label = "dotColor"
                    )
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    )
                }
            }

            Button(
                onClick = {
                    if (last) onFinish()
                    else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Black,
                    contentColor = Color.White
                )
            ) {
                Text(
                    stringResource(if (last) R.string.tour_done else R.string.tour_next),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * A small mock of the surface each page is describing.
 *
 * Deliberately not interactive and deliberately not real data: it is a picture of the
 * thing, sized and coloured like the thing, so the sentence underneath has something to
 * point at.
 */
@Composable
private fun PageArtwork(page: TourPage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Black),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            when (page) {
                TourPage.WHAT -> {
                    MockLabel("TOTAL")
                    MockAmount("₦1,200,000")
                    Spacer(Modifier.height(16.dp))
                    MockBar("Catering", 0.7f)
                    MockBar("Venue", 0.45f)
                    MockBar("Decor", 0.2f)
                }

                TourPage.TEMPLATES -> {
                    MockLabel("TEMPLATES FOR WEDDING")
                    Spacer(Modifier.height(10.dp))
                    MockRow("Traditional Wedding")
                    MockRow("White Wedding & Reception")
                    MockRow("Engagement")
                }

                TourPage.LOGGING -> {
                    MockLabel("ADD EXPENSE")
                    MockAmount("₦320,000")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MockChip("Unpaid", selected = false)
                        MockChip("Deposit paid", selected = true)
                        MockChip("Paid", selected = false)
                    }
                }

                TourPage.INSIGHTS -> {
                    MockLabel("OVERVIEW")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MockFigure("Spent", "₦840,000", Color(0xFFFF6B6B))
                        MockFigure("Left", "₦360,000", Color(0xFF6EE7B7))
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Color(0xFFFBBF24),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "70% of the total used · 12 days left",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MockLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 0.5.sp)
}

@Composable
private fun MockAmount(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    )
}

@Composable
private fun MockFigure(label: String, amount: String, color: Color) {
    Column {
        Text(amount, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun MockBar(label: String, fraction: Float) {
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun MockRow(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MockChip(label: String, selected: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) AppColors.Black else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
