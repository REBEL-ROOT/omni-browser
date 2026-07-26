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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ── Creamy Light Design Tokens (Google / Firefox Soothing Palette) ──────────────
private val CreamyLightBackground = Color(0xFFFAF8F5)  // Soothing Creamy Off-White / Ivory
private val CardBg                 = Color(0xFFFFFFFF)  // Pure White Surface Card
private val CardBorderColor        = Color(0xFFE2E8F0)  // Soothing Muted Border
private val TitleTextColor         = Color(0xFF0F172A)  // Deep Charcoal Slate Text
private val SubtextColor           = Color(0xFF475569)  // Soothing Dark Subtext
private val PhoneBorderColor       = Color(0xFFCBD5E1)  // Light Grey Device Bezel

data class OnboardingPage(
    val id: String,
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
    context: Context,
    onFinish: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Navbar selection state for 6th page ("Split" | "Top" | "Bottom")
    var selectedNavbarPos by remember { mutableStateOf(viewModel.addressBarPosition) }
    var autoCycleNavbar by remember { mutableStateOf(true) }

    val pages = remember {
        listOf(
            OnboardingPage(
                id = "browse",
                imageRes = R.drawable.ob_secure_browser,
                title = "Browse Freely,\nBrowse Privately",
                accentColor = Color(0xFF0284C7), // Soothing Ocean Blue
                tagline = "Zero Trackers · Fully On-Device",
                features = listOf(
                    FeatureItem(Icons.Rounded.Search,          "Smart Address Bar",    "Voice, text, and camera search integrated right in omnibox"),
                    FeatureItem(Icons.Rounded.Newspaper,       "Discover Feed",        "Latest news headlines by category — zero account required"),
                    FeatureItem(Icons.Rounded.VisibilityOff,   "Incognito Mode",       "No history, no cookies, no tracking — 1-tap instant switch"),
                    FeatureItem(Icons.Rounded.ContentCopy,     "Universal Copy",       "Bypass copy protection to select and copy text from any webpage")
                )
            ),
            OnboardingPage(
                id = "tools",
                imageRes = R.drawable.ob_quick_tools,
                title = "16 Quick Tools,\nOne Browser",
                accentColor = Color(0xFF0D9488), // Soothing Teal
                tagline = "Built-in Utilities · No Extra Apps Needed",
                features = listOf(
                    FeatureItem(Icons.Rounded.QrCodeScanner,   "QR Code Scanner",      "Scan QR codes directly from any webpage or image"),
                    FeatureItem(Icons.Rounded.Description,     "Save as Clean PDF",    "Export articles as clean, formatted PDFs for offline reading"),
                    FeatureItem(Icons.Rounded.Code,            "Developer Inspector",  "Inspect HTML, view console logs, and edit live CSS"),
                    FeatureItem(Icons.Rounded.Apps,            "Web App Shortcuts",    "Pin your favorite websites as native apps on your home screen")
                )
            ),
            OnboardingPage(
                id = "extensions",
                imageRes = R.drawable.ob_extensions_vault,
                title = "Real Extensions,\nReal Protection",
                accentColor = Color(0xFFE11D48), // Soothing Firefox Rose
                tagline = "uBlock Origin · Firefox Add-ons · Safe Locker",
                features = listOf(
                    FeatureItem(Icons.Rounded.Shield,          "uBlock Origin Native", "Official Firefox desktop ad blocker — block ads and trackers automatically"),
                    FeatureItem(Icons.Rounded.AddCircleOutline,"Firefox Store Access", "Install any desktop extension directly from Mozilla Store"),
                    FeatureItem(Icons.Rounded.Block,           "AI Overview Blocker",  "Hide forced AI summaries on Google & Bing search results"),
                    FeatureItem(Icons.Rounded.Lock,            "Biometric Safe Vault", "Encrypted vault for downloads, images, and documents")
                )
            ),
            OnboardingPage(
                id = "media",
                imageRes = R.drawable.ob_media_player,
                title = "Media Downloader\n& Quick Access",
                accentColor = Color(0xFFEA580C), // Soothing Warm Amber
                tagline = "Bookmarks · History · Downloads · Burn",
                features = listOf(
                    FeatureItem(Icons.Rounded.BookmarkBorder,  "Bookmarks & History",  "Quick tab drawer with history, bookmarks, and tab groups"),
                    FeatureItem(Icons.Rounded.PlayCircle,      "Media Sniffer",        "Download videos, audio clips, and HLS streams from any site"),
                    FeatureItem(Icons.Rounded.PlayArrow,       "Background Player",    "Background audio play, speed controls, and PiP floating player"),
                    FeatureItem(Icons.Rounded.LocalFireDepartment, "1-Tap Burn Mode",   "Instantly wipe all tabs, history, and cache with a single tap")
                )
            ),
            OnboardingPage(
                id = "default_browser",
                imageRes = R.drawable.ob_secure_browser,
                title = "Set Omni as Your\nDefault Browser",
                accentColor = Color(0xFF2563EB), // Royal Blue
                tagline = "Fast · Private · Secure Every Day",
                features = listOf(
                    FeatureItem(Icons.Rounded.Shield,          "Automatic Protection", "Block tracking scripts, popups, and harmful ads on every link"),
                    FeatureItem(Icons.Rounded.Bolt,            "Lightning Fast Load",  "Instant page rendering without background telemetry slowdowns"),
                    FeatureItem(Icons.Rounded.Lock,            "Encrypted & Safe",     "Biometric protection and automatic HTTPS encryption for all sites")
                )
            ),
            OnboardingPage(
                id = "navbar_position",
                imageRes = R.drawable.ob_nav_split,
                title = "Choose Your\nNavigation Layout",
                accentColor = Color(0xFF6366F1), // Soothing Indigo
                tagline = "1st Split · 2nd Top · 3rd Bottom",
                features = emptyList() // Suggestions removed per user request
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1
    val currentPage = pages[pagerState.currentPage]

    // Auto-cycle navbar layout animation on 6th page if user hasn't tapped manually
    LaunchedEffect(pagerState.currentPage, autoCycleNavbar) {
        if (pages[pagerState.currentPage].id == "navbar_position" && autoCycleNavbar) {
            val positions = listOf("Split", "Top", "Bottom")
            var idx = positions.indexOf(selectedNavbarPos).coerceAtLeast(0)
            while (autoCycleNavbar && pagerState.currentPage == pages.size - 1) {
                delay(3200)
                idx = (idx + 1) % positions.size
                val nextPos = positions[idx]
                selectedNavbarPos = nextPos
                viewModel.saveAddressBarPosition(context, nextPos)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamyLightBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        // ── Top Bar: Step Chip & Skip ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step Chip
            Surface(
                shape = CircleShape,
                color = CardBg,
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Text(
                    text = "STEP ${pagerState.currentPage + 1} OF ${pages.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentPage.accentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // Skip Button
            if (!isLastPage) {
                Text(
                    text = "Skip",
                    color = SubtextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.saveOnboardingCompleted(context, true)
                            onFinish()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ── Pager Content ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 86.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { page ->
                val pageData = pages[page]

                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (pageOffset.absoluteValue * 0.04f).coerceIn(0f, 0.04f)
                val alpha = 1f - (pageOffset.absoluteValue * 0.3f).coerceIn(0f, 0.3f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {

                    if (pageData.id == "navbar_position") {
                        // ── Cartoonish Vector Animated Navbar Showcase Component ──
                        AnimatedNavbarShowcase(
                            selectedPos = selectedNavbarPos,
                            modifier = Modifier
                                .height(340.dp)
                                .fillMaxWidth()
                        )
                    } else {
                        // ── Phone Mockup Screenshot for Pages 1–5 ─────────────────
                        PhoneMockup(
                            imageRes = pageData.imageRes,
                            modifier = Modifier
                                .height(225.dp)
                                .wrapContentWidth()
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Title ────────────────────────────────────────────────────────
                    Text(
                        text = pageData.title,
                        color = TitleTextColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // ── Tagline Chip ─────────────────────────────────────────────────
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorderColor)
                    ) {
                        Text(
                            text = if (pageData.id == "navbar_position") "Active: $selectedNavbarPos Layout" else pageData.tagline,
                            color = pageData.accentColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── Interactive Navbar Position Chooser on Page 6 ──────────────
                    if (pageData.id == "navbar_position") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select Preferred Layout",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = TitleTextColor
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Split", "Top", "Bottom").forEach { pos ->
                                        val isSelected = (selectedNavbarPos == pos)
                                        Surface(
                                            onClick = {
                                                autoCycleNavbar = false
                                                selectedNavbarPos = pos
                                                viewModel.saveAddressBarPosition(context, pos)
                                                Toast.makeText(context, "Layout set to $pos", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) Color(0xFF6366F1) else CreamyLightBackground,
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else CardBorderColor),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Text(
                                                    text = when(pos) {
                                                        "Split" -> "1st Split"
                                                        "Top" -> "2nd Top"
                                                        else -> "3rd Bottom"
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else TitleTextColor
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        viewModel.saveAddressBarPosition(context, selectedNavbarPos)
                                        viewModel.saveOnboardingCompleted(context, true)
                                        onFinish()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Text("Set Layout & Start Browsing", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // ── Direct 1-Tap uBlock Origin Reference Installer on Slide 3 ──
                    if (pageData.id == "extensions") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Install uBlock Origin",
                                            color = TitleTextColor,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "1-Tap automatic background install",
                                            color = SubtextColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.installExtensionFromUrl("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon-607454-latest.xpi", context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Text("Install", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // ── Special Action Card on Slide 5 (Set Default Browser) ────
                    if (pageData.id == "default_browser") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardBg,
                            border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Make Omni Browser Your Default",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TitleTextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Open system settings to select Omni Browser as your default browser",
                                    fontSize = 11.5.sp,
                                    color = SubtextColor,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        openDefaultBrowserSettings(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Text("Set as Default Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // ── Feature Card List (Shown for pages 1–5) ───────────────────
                    if (pageData.id != "navbar_position") {
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
        }

        // ── Bottom Bar: Page Dots & Navigation ──────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Page Indicator Dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isActive) 20.dp else 5.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                        label = "dot_w_$index"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) pages[pagerState.currentPage].accentColor
                                      else Color(0xFFCBD5E1),
                        animationSpec = tween(200),
                        label = "dot_c_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 5.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Get Started Action Button
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.saveAddressBarPosition(context, selectedNavbarPos)
                        viewModel.saveOnboardingCompleted(context, true)
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.height(46.dp),
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
                        text = if (isLastPage) "Set & Finish" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = if (isLastPage) Icons.Rounded.CheckCircle
                                      else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Cartoonish Vector Animated Showcase Component for Navigation Bar Layouts.
 * Demonstrates Split, Top, and Bottom navigation styles with live animated Compose vectors.
 */
@Composable
private fun AnimatedNavbarShowcase(
    selectedPos: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Cartoon Device Frame (Wider aspect ratio for spacious flexing layout)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.56f)
                .shadow(12.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0F172A)) // Sleek Dark Slate Phone Bezel
                .border(width = 2.dp, color = Color(0xFF334155), shape = RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Device Status Bar (Ultra-Slim Minimalist Notch Line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    // Ultra-Slim Camera Speaker Line Notch
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SignalCellular4Bar, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(10.dp))
                        Icon(Icons.Rounded.BatteryFull, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(10.dp))
                    }
                }

                // ── Webpage View Canvas with Animated Top & Bottom Navbars ─────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC)) // Clean Minimalist Light Slate Canvas
                ) {
                    // ── 1. Animated Top Bar Container ──────────────────────────────
                    Box(modifier = Modifier.align(Alignment.TopCenter)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = (selectedPos == "Split" || selectedPos == "Top"),
                            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)) + fadeOut()
                        ) {
                            if (selectedPos == "Split") {
                                // Split Top Bar (Address Bar Only - Flush Top, No Top Border Line)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF0F172A))
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Home, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = "rebelroot.xyz",
                                            color = Color(0xFFF8FAFC),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Rounded.StarBorder, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF312E81)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Extension, null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(15.dp))
                                    }
                                }
                            } else {
                                // Top Unified Bar (All-in-One Top Navbar - Flush Top, No Top Border Line)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF0F172A))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Home, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.GridView, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = "rebelroot.xyz",
                                            color = Color(0xFFF8FAFC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    TabCountBadge(boxSize = 20.dp)
                                    Icon(Icons.Rounded.Menu, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // ── 2. Animated Bottom Bar Container ───────────────────────────
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = (selectedPos == "Split" || selectedPos == "Bottom"),
                            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut()
                        ) {
                            if (selectedPos == "Split") {
                                // Split Bottom Action Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF0F172A))
                                        .border(BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f)))
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                                    Icon(Icons.Rounded.GridView, null, tint = Color(0xFF818CF8), modifier = Modifier.size(19.dp))
                                    TabCountBadge(boxSize = 21.dp)
                                    Icon(Icons.Rounded.Menu, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(17.dp))
                                }
                            } else {
                                // Bottom Unified Bar (All-in-One Bottom Navbar)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color(0xFF0F172A))
                                        .border(BorderStroke(1.dp, Color(0xFF6366F1)))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Home, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.GridView, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1E293B))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = "rebelroot.xyz",
                                            color = Color(0xFFF8FAFC),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    TabCountBadge(boxSize = 20.dp)
                                    Icon(Icons.Rounded.Menu, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dead-centered Tab Count Badge icon box [ 1 ].
 * Ensures the number "1" is mathematically centered from top, bottom, left, and right.
 */
@Composable
private fun TabCountBadge(
    count: String = "1",
    modifier: Modifier = Modifier,
    boxSize: androidx.compose.ui.unit.Dp = 20.dp,
    borderColor: Color = Color(0xFFCBD5E1),
    textColor: Color = Color(0xFFF8FAFC)
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .border(1.2.dp, borderColor, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count,
            color = textColor,
            fontSize = (boxSize.value * 0.48f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

/**
 * Clean device mockup for creamy light background.
 */
@Composable
private fun PhoneMockup(
    imageRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.46f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0F172A))
                .border(
                    width = 1.5.dp,
                    color = PhoneBorderColor,
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.FillBounds,
                alignment = Alignment.TopCenter
            )
        }
    }
}

/**
 * Creamy light feature row with crisp icons.
 */
@Composable
private fun FeatureRow(
    feature: FeatureItem,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(12.dp))
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                color = TitleTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )
            Text(
                text = feature.detail,
                color = SubtextColor,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Standard Android intent helper to launch default apps settings or browser role dialog.
 */
private fun openDefaultBrowserSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                if (context is Activity) {
                    context.startActivityForResult(intent, 1001)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return
            }
        }
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
