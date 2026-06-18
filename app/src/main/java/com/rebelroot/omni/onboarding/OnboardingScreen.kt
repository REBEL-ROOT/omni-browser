package com.rebelroot.omni.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

// Premium Theme Colors (Obsidian Dark System)
private val ObsidianBgStart = Color(0xFF06090E)
private val ObsidianBgEnd = Color(0xFF0C1420)
private val CardBorderColor = Color(0x1FADBAF7)
private val GlassCardBg = Color(0x1406090E)

data class OnboardingPageData(
    val imageRes: Int,
    val title: String,
    val subtitle: String,
    val description: String,
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
    val pages = remember {
        listOf(
            OnboardingPageData(
                imageRes = R.drawable.ob_secure_browser,
                title = "Security First",
                subtitle = "No External APIs & Telemetry",
                description = "Omni Browser runs fully locally. We make zero external API calls to trackers or central servers, keeping your browsing fully private and secure.",
                accentColor = Color(0xFF0A84FF) // Ocean Blue
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_quick_tools,
                title = "Developer Tools",
                subtitle = "Edit Webpages Real-time",
                description = "Inspect source code, manipulate the live DOM, or inject custom CSS/JS scripts on any page with our powerful integrated Quick Tools.",
                accentColor = Color(0xFF5E5CE6) // Royal Purple
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_save_pdf,
                title = "Web page to PDF",
                subtitle = "Clean Formatting Export",
                description = "Convert any complex web article into a clean, read-optimized PDF document instantly. Ideal for offline reading and research.",
                accentColor = Color(0xFF30D158) // Emerald Green
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_media_player,
                title = "Media Hub",
                subtitle = "Native Player & Downloader",
                description = "Stream video feeds through our hardware-accelerated Media3 player with gesture controls, background playback, and multi-threaded parallel downloads.",
                accentColor = Color(0xFFFF6D00) // Sunset Orange
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_qr_scanner,
                title = "QR Tools",
                subtitle = "Smart Scan Page & Share QR",
                description = "Scan any QR codes visible on webpages instantly using our Google Lens-style scanner, or generate live sharing QR codes for links in real-time.",
                accentColor = Color(0xFF00D2C4) // Teal/Cyan
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_extensions_vault,
                title = "Extensions & Vault",
                subtitle = "Firefox Extensions & Secure Locker",
                description = "Install desktop extensions (like uBlock Origin) to block ads, and lock your sensitive downloads behind biometrically encrypted local vault storage.",
                accentColor = Color(0xFFFF3B5C) // Crimson Red
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBgStart, ObsidianBgEnd)
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // --- Top Bar (Skip Button) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            AnimatedVisibility(
                visible = !isLastPage,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Skip",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
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

        // --- Pager & Content ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 32.dp)
            ) { page ->
                val pageData = pages[page]

                // Page offset calculations for premium offset translation animations
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 0.15f)
                val alpha = 1f - (pageOffset.absoluteValue * 0.7f).coerceIn(0f, 0.7f)
                val rotationY = pageOffset * -30f // 3D Book page turn feel

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.scaleX = scale
                            this.scaleY = scale
                            this.alpha = alpha
                            this.rotationY = rotationY
                            this.cameraDistance = 8 * density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration Card (Glassmorphic border/shadow)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 32.dp,
                                shape = RoundedCornerShape(28.dp),
                                clip = false,
                                ambientColor = pageData.accentColor.copy(alpha = 0.2f),
                                spotColor = pageData.accentColor.copy(alpha = 0.4f)
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(GlassCardBg)
                            .border(1.5.dp, CardBorderColor, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = pageData.imageRes),
                            contentDescription = pageData.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Text Content Card
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title
                        Text(
                            text = pageData.title,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Subtitle with Accent Color
                        Text(
                            text = pageData.subtitle,
                            color = pageData.accentColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    pageData.accentColor.copy(alpha = 0.12f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        Text(
                            text = pageData.description,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        // --- Bottom Navigation & Indicators ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            // Page Indicators (Animated Dots)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    val active = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (active) 22.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (active) pages[pagerState.currentPage].accentColor else Color.White.copy(alpha = 0.2f),
                        animationSpec = tween(durationMillis = 300),
                        label = "dot_color"
                    )

                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Action Button
            val activeAccentColor = pages[pagerState.currentPage].accentColor
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeAccentColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Icon(
                        imageVector = if (isLastPage) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = if (isLastPage) "Get Started" else "Next",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
