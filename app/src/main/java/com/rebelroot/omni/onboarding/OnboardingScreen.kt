/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ── Design tokens ──────────────────────────────────────────────────────────────
private val BgGradientTop    = Color(0xFF0A1628)
private val BgGradientBottom = Color(0xFF0F2137)
private val SubtextColor     = Color(0xFF8A9BBD)
private val FeatureRowBg     = Color(0x14FFFFFF)   // 8% white — subtle row background
private val PhoneBorderColor = Color(0xFF2D4A6E)   // Muted blue-slate border
private val PhoneHomebar     = Color(0x50FFFFFF)   // Semi-transparent white

/**
 * One onboarding slide.
 *
 * [features] are shown as a simple bullet list below the phone screenshot —
 * the same pattern used by Google, Apple, and Samsung on their setup screens.
 * No overlaid callouts, no floating animations, no clipping issues.
 */
data class OnboardingPage(
    val imageRes: Int,
    val title: String,
    val accentColor: Color,
    val tagline: String,
    val features: List<FeatureItem>
)

data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val detail: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: BrowserViewModel,
    context: android.content.Context,
    onFinish: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 4 carefully chosen slides — each mapped to a real screenshot.
    // Features listed below the screenshot describe what the user is looking at,
    // like Google's Pixel setup flow or Apple's iOS intro screens.
    val pages = remember {
        listOf(
            OnboardingPage(
                imageRes = R.drawable.ob_secure_browser,
                title = "Browse Freely,\nBrowse Privately",
                accentColor = Color(0xFF0A84FF),
                tagline = "Zero Trackers · Fully On-Device",
                features = listOf(
                    FeatureItem(Icons.Rounded.Search,          "Smart Address Bar",    "Search or enter any URL — voice and camera search built in"),
                    FeatureItem(Icons.Rounded.Newspaper,       "Discover Feed",        "Trending news by topic, refreshed without any account needed"),
                    FeatureItem(Icons.Rounded.VisibilityOff,   "Incognito Mode",       "No history, no cookies, no trace — one tap from home screen")
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_quick_tools,
                title = "12 Tools,\nOne Browser",
                accentColor = Color(0xFF5E5CE6),
                tagline = "Everything You Need · No Extra Apps",
                features = listOf(
                    FeatureItem(Icons.Rounded.QrCodeScanner,   "QR Scanner",           "Scan QR codes visible on any webpage instantly"),
                    FeatureItem(Icons.Rounded.Description,     "Save as PDF",          "Export any article as a clean, readable PDF offline"),
                    FeatureItem(Icons.Rounded.Code,            "Developer Tools",      "Inspect HTML, inject CSS/JS, and view console logs live")
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_extensions_vault,
                title = "Real Extensions,\nReal Protection",
                accentColor = Color(0xFFFF3B5C),
                tagline = "Firefox Extensions · Biometric Vault",
                features = listOf(
                    FeatureItem(Icons.Rounded.Extension,       "uBlock Origin",        "Full Firefox desktop ad blocker — runs natively on GeckoView"),
                    FeatureItem(Icons.Rounded.Shield,          "AI Blocker",           "Hides AI overview summaries from Google and Bing search"),
                    FeatureItem(Icons.Rounded.Lock,            "Safe Locker",          "Fingerprint-encrypted vault for images, docs, and videos")
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_media_player,
                title = "Browser Menu\n& Quick Access",
                accentColor = Color(0xFFFF6D00),
                tagline = "Bookmarks · History · Downloads · Burn",
                features = listOf(
                    FeatureItem(Icons.Rounded.BookmarkBorder,  "Bookmarks & History",  "Saved sites and full browsing history always one tap away"),
                    FeatureItem(Icons.Rounded.PlayCircle,      "Media Downloader",     "Grab videos, audio, and HLS streams from any page"),
                    FeatureItem(Icons.Rounded.LocalFireDepartment, "Burn Mode",        "Instantly wipes all tabs, history, and session cookies")
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1
    val currentPage = pages[pagerState.currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgGradientTop, BgGradientBottom)))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        // ── Skip ────────────────────────────────────────────────────────────────
        if (!isLastPage) {
            Text(
                text = "Skip",
                color = SubtextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.saveOnboardingCompleted(context, true)
                        onFinish()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // ── Pager ────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 52.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1   // Pre-load neighbour to reduce first-swipe jank
            ) { page ->
                val pageData = pages[page]

                // Lightweight page transition: scale + alpha only.
                // rotationY (3D book-flip) was removed — it forces a GPU compositing
                // layer on every frame during a drag gesture, causing consistent jank.
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (pageOffset.absoluteValue * 0.06f).coerceIn(0f, 0.06f)
                val alpha = 1f - (pageOffset.absoluteValue * 0.4f).coerceIn(0f, 0.4f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {

                    // ── Phone mockup ─────────────────────────────────────────────
                    // The frame is a border-only rounded rectangle — no filled
                    // background — so it never bleeds into the page background colour.
                    PhoneMockup(
                        imageRes = pageData.imageRes,
                        accentColor = pageData.accentColor,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    // ── Title ───────────────────────────────────────────────────
                    Text(
                        text = pageData.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // ── Tagline chip ────────────────────────────────────────────
                    Text(
                        text = pageData.tagline,
                        color = pageData.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                pageData.accentColor.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Feature list ────────────────────────────────────────────
                    // Simple rows with icon + title + one-line description.
                    // This is the pattern used by Google (Pixel setup), Apple (iOS intro),
                    // and Samsung (Galaxy setup) — clean, readable, no positioning risk.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pageData.features.forEach { feature ->
                            FeatureRow(
                                feature = feature,
                                accentColor = pageData.accentColor
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom nav ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Page indicator dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 7.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_w_$index"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) pages[pagerState.currentPage].accentColor
                                      else Color.White.copy(alpha = 0.25f),
                        animationSpec = tween(250),
                        label = "dot_c_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 7.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Get Started — colour tracks the current slide accent
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.saveOnboardingCompleted(context, true)
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentPage.accentColor,
                    contentColor = Color.White
                ),
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Icon(
                        imageVector = if (isLastPage) Icons.Rounded.CheckCircle
                                      else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * A minimal phone mockup that wraps a real app screenshot.
 *
 * Design decisions:
 *  - The "frame" is a border only (no [Modifier.background] fill) so it never
 *    creates an opaque rectangle behind itself that overflows the page gradient.
 *  - The screenshot is clipped to [RoundedCornerShape] matching the inner radius
 *    of the border, producing a seamless screen-inside-bezel look.
 *  - A thin accent-coloured glow line at the top gives each slide a unique visual
 *    identity without adding heavy shadow layers that would slow compositing.
 *  - The home-indicator bar at the bottom is the only decoration — no speaker
 *    cutout, camera notch, or status-bar overlay. These added layout complexity
 *    and weren't visible at the mockup size.
 */
@Composable
private fun PhoneMockup(
    imageRes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.48f)    // ~portrait phone ratio
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = accentColor.copy(alpha = 0.2f),
                    spotColor = accentColor.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(36.dp))
                .border(
                    width = 2.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.7f),   // accent glow at top
                            PhoneBorderColor,                  // neutral border rest
                            PhoneBorderColor
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                )
        ) {
            // Screenshot fills the entire mockup — no padding gap around it
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(34.dp)),   // 2dp inset from border
                contentScale = ContentScale.Crop
            )

            // Home indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PhoneHomebar)
            )
        }
    }
}

/**
 * A single feature row: accent icon in a pill on the left, title + one-line
 * description on the right. Same layout pattern used in Google's Pixel setup
 * app and Apple's iOS feature introduction screens.
 *
 * No animations — this is static content rendered once per composition.
 */
@Composable
private fun FeatureRow(
    feature: FeatureItem,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FeatureRowBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
            Text(
                text = feature.detail,
                color = SubtextColor,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
