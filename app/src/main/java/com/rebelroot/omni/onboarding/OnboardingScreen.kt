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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.foundation.Image

// Design tokens
private val BgStart   = Color(0xFF0D1B2A)   // Deep navy — matches the app's dark theme
private val BgMid     = Color(0xFF11263A)
private val BgEnd     = Color(0xFF0A1628)
private val CardBg    = Color(0xFF162032)
private val SubtextColor = Color(0xFF94A3B8)
private val PhoneBezel   = Color(0xFF1E293B)
private val PhoneSpeaker = Color(0xFF334155)

/**
 * Data model for a single onboarding slide.
 *
 * [callouts] are pointer labels drawn over the phone screenshot to highlight
 * specific UI elements — this replaces the old floating badge system which
 * had no visual connection to the actual screenshot content.
 *
 * Performance note: [imageRes] should point to a PNG/JPG in drawable/, not
 * drawable-nodpi/, so the system can select density-appropriate variants.
 * Current screenshots are ~150-190KB which is acceptable for onboarding.
 */
data class OnboardingPage(
    val imageRes: Int,
    val title: String,
    val accentColor: Color,
    val tagline: String,
    val description: String,
    val callouts: List<Callout>
)

/**
 * A pointer callout drawn over the phone screenshot.
 * [xFraction] / [yFraction] are 0–1 positions within the phone screen area.
 * [icon] is a Material icon shown in a small pill next to the pointer dot.
 */
data class Callout(
    val icon: ImageVector,
    val label: String,
    val xFraction: Float,   // 0 = left edge, 1 = right edge of phone screen
    val yFraction: Float,   // 0 = top, 1 = bottom
    val accentColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: BrowserViewModel,
    context: android.content.Context,
    onFinish: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 4 slides — reduced from 6. Merged "QR Tools" into "Quick Tools" slide
    // and removed the separate "Web page to PDF" slide (now a callout on Quick Tools).
    // Each slide uses a real screenshot from the app, not an AI illustration.
    val pages = remember {
        listOf(
            OnboardingPage(
                imageRes = R.drawable.ob_secure_browser,
                title = "Your Browser,\nYour Rules",
                accentColor = Color(0xFF0A84FF),
                tagline = "Privacy First — No Trackers, No Telemetry",
                description = "Omni runs entirely on-device. Zero calls to tracking servers. Your history, tabs, and data never leave your phone.",
                callouts = listOf(
                    Callout(Icons.Rounded.Search, "Search", 0.5f, 0.30f, Color(0xFF0A84FF)),
                    Callout(Icons.Rounded.VisibilityOff, "Incognito", 0.22f, 0.55f, Color(0xFF5E5CE6)),
                    Callout(Icons.Rounded.Newspaper, "Discover", 0.5f, 0.75f, Color(0xFF30D158))
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_quick_tools,
                title = "Powerful\nQuick Tools",
                accentColor = Color(0xFF5E5CE6),
                tagline = "12 Built-in Tools — One Tap Away",
                description = "QR Scanner, PDF Export, Page Translator, DOM Inspector, Auto-Scroll, Dev Console and more — all without leaving the browser.",
                callouts = listOf(
                    Callout(Icons.Rounded.QrCodeScanner, "QR Scan", 0.22f, 0.42f, Color(0xFF00D2C4)),
                    Callout(Icons.Rounded.Description, "Save PDF", 0.22f, 0.60f, Color(0xFF30D158)),
                    Callout(Icons.Rounded.Code, "Dev Tools", 0.78f, 0.78f, Color(0xFF5E5CE6))
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_extensions_vault,
                title = "Extensions\n& Safe Vault",
                accentColor = Color(0xFFFF3B5C),
                tagline = "Real Firefox Extensions + Biometric Locker",
                description = "Install uBlock Origin and other Firefox desktop extensions. Lock sensitive files behind fingerprint-encrypted vault storage.",
                callouts = listOf(
                    Callout(Icons.Rounded.Extension, "uBlock", 0.25f, 0.38f, Color(0xFFFF3B5C)),
                    Callout(Icons.Rounded.Lock, "Vault", 0.75f, 0.62f, Color(0xFFFF9F0A)),
                    Callout(Icons.Rounded.Shield, "AI Block", 0.25f, 0.72f, Color(0xFF0A84FF))
                )
            ),
            OnboardingPage(
                imageRes = R.drawable.ob_media_player,
                title = "Browser +\nMedia Hub",
                accentColor = Color(0xFFFF6D00),
                tagline = "Native Player, Downloads & Burn Mode",
                description = "Stream and download media with gesture controls and background playback. Burn mode clears all session data instantly with one tap.",
                callouts = listOf(
                    Callout(Icons.Rounded.BookmarkBorder, "Bookmarks", 0.5f, 0.28f, Color(0xFFFF6D00)),
                    Callout(Icons.Rounded.PlayCircle, "Media", 0.75f, 0.50f, Color(0xFFFF3B5C)),
                    Callout(Icons.Rounded.LocalFireDepartment, "Burn", 0.25f, 0.65f, Color(0xFFFF453A))
                )
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgStart, BgMid, BgEnd))
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        // Skip button — top right, hidden on last page
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

        // Pager
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // No horizontal padding — phone frame fills the width naturally
                beyondViewportPageCount = 1   // Pre-load adjacent page to reduce jank on swipe
            ) { page ->
                val pageData = pages[page]

                // Lightweight scale+alpha only — rotationY was removed because the
                // 3D perspective transform forces a GPU layer on every frame during
                // the swipe gesture and was the primary source of frame drops.
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (pageOffset.absoluteValue * 0.08f).coerceIn(0f, 0.08f)
                val alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 0.5f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            // renderEffect / layer hint: tell RenderThread this column
                            // will animate, so it stays on its own hardware layer.
                            // This avoids re-rasterising the static screenshot on every frame.
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // Phone frame with real screenshot + callout pointers
                    // Only pass isCurrentPage so infinite animations only run on the visible slide.
                    PhoneFrameWithScreenshot(
                        imageRes = pageData.imageRes,
                        callouts = pageData.callouts,
                        accentColor = pageData.accentColor,
                        isCurrentPage = page == pagerState.currentPage,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Text section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = pageData.title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        // Accent tagline pill
                        Text(
                            text = pageData.tagline,
                            color = pageData.accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    pageData.accentColor.copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = pageData.description,
                            color = SubtextColor,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        // Bottom nav — dots + button
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
                        targetValue = if (isActive) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_w_$index"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) pages[pagerState.currentPage].accentColor
                                      else Color.White.copy(alpha = 0.2f),
                        animationSpec = tween(250),
                        label = "dot_c_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Get Started button
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.saveOnboardingCompleted(context, true)
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pages[pagerState.currentPage].accentColor,
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
 * Renders a phone-frame bezel containing the real app screenshot, overlaid with
 * animated callout pointers that highlight key UI elements on the screenshot.
 *
 * Performance decisions:
 *  - [isCurrentPage]: infinite pulse animations on callout dots only run when this
 *    page is the active one. Running them on background pages wastes Choreographer
 *    frames and was a measured source of jank.
 *  - No `shadow()` inside the pager content — hardware-layer composites don't
 *    interact well with shadow blur during the swipe gesture.
 *  - The phone frame shape is drawn with simple Canvas-level modifiers, not a
 *    separate Image drawable, to avoid an extra bitmap decode.
 */
@Composable
private fun PhoneFrameWithScreenshot(
    imageRes: Int,
    callouts: List<Callout>,
    accentColor: Color,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation for callout dots — only active on the current page
    val infiniteTransition = rememberInfiniteTransition(label = "callout_pulse")
    val pulseScale by if (isCurrentPage) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Phone outer bezel
        Box(
            modifier = Modifier
                .fillMaxHeight(0.88f)
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(36.dp))
                .background(PhoneBezel)
                .border(2.dp, PhoneSpeaker, RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Speaker notch
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .size(width = 48.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(PhoneSpeaker)
            )

            // Screen area with the real screenshot
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 28.dp,
                        bottom = 10.dp,
                        start = 6.dp,
                        end = 6.dp
                    )
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Callout overlay — drawn using BoxWithConstraints so we can
                // convert xFraction/yFraction to actual pixel offsets.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val w = maxWidth
                    val h = maxHeight
                    callouts.forEach { callout ->
                        CalloutPointer(
                            callout = callout,
                            x = w * callout.xFraction,
                            y = h * callout.yFraction,
                            pulseScale = pulseScale,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }

            // Home indicator bar at bottom of phone
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PhoneSpeaker)
            )
        }
    }
}

/**
 * A single callout pointer: a pulsing dot at [x,y] with an icon+label pill
 * floating to the side. The pill side (left or right) is chosen automatically
 * based on whether the point is in the left or right half of the screen,
 * so labels never clip off the edge.
 */
@Composable
private fun CalloutPointer(
    callout: Callout,
    x: Dp,
    y: Dp,
    pulseScale: Float,
    modifier: Modifier = Modifier
) {
    val pillOnLeft = callout.xFraction > 0.5f   // If point is on right, pill goes left

    Box(modifier = modifier.offset(x = x, y = y)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.offset(
                x = if (pillOnLeft) (-120).dp else 6.dp,
                y = (-12).dp
            )
        ) {
            if (!pillOnLeft) {
                // Dot on left, pill on right
                PulseDot(callout.accentColor, pulseScale)
                CalloutPill(callout)
            } else {
                // Pill on left, dot on right
                CalloutPill(callout)
                PulseDot(callout.accentColor, pulseScale)
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color, pulseScale: Float) {
    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        Box(
            modifier = Modifier
                .size((14 * pulseScale).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
        )
        // Inner solid dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun CalloutPill(callout: Callout) {
    Row(
        modifier = Modifier
            .background(
                Color(0xCC0D1B2A),   // Dark translucent — readable over any screenshot
                RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = callout.accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = callout.icon,
            contentDescription = null,
            tint = callout.accentColor,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = callout.label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
