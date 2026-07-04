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
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
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

// Soft light cyan/white gradient and slate-contrast tokens (copied from MyPdf UI pattern)
private val BgStart = Color(0xFFCBEFF4)
private val BgEnd = Color(0xFFFFFFFF)
private val DarkSlate = Color(0xFF07212F)
private val SubtextColor = Color(0xFF475569)

data class OnboardingPageData(
    val imageRes: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color,
    val badge1Icon: ImageVector,
    val badge1Text: String,
    val badge2Icon: ImageVector,
    val badge2Text: String
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
                accentColor = Color(0xFF0A84FF),
                badge1Icon = Icons.Rounded.Security,
                badge1Text = "Secure",
                badge2Icon = Icons.Rounded.VisibilityOff,
                badge2Text = "No APIs"
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_quick_tools,
                title = "Developer Tools",
                subtitle = "Edit Webpages Real-time",
                description = "Inspect source code, manipulate the live DOM, or inject custom CSS/JS scripts on any page with our powerful integrated Quick Tools.",
                accentColor = Color(0xFF5E5CE6),
                badge1Icon = Icons.Rounded.Code,
                badge1Text = "DOM",
                badge2Icon = Icons.Rounded.Computer,
                badge2Text = "Scripts"
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_save_pdf,
                title = "Web page to PDF",
                subtitle = "Clean Formatting Export",
                description = "Convert any complex web article into a clean, read-optimized PDF document instantly. Ideal for offline reading and research.",
                accentColor = Color(0xFF30D158),
                badge1Icon = Icons.Rounded.Description,
                badge1Text = "PDF",
                badge2Icon = Icons.Rounded.ArrowDownward,
                badge2Text = "Clean"
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_media_player,
                title = "Media Hub",
                subtitle = "Native Player & Downloader",
                description = "Stream video feeds through our hardware-accelerated Media3 player with gesture controls, background playback, and multi-threaded parallel downloads.",
                accentColor = Color(0xFFFF6D00),
                badge1Icon = Icons.Rounded.PlayCircle,
                badge1Text = "Player",
                badge2Icon = Icons.Rounded.ArrowDownward,
                badge2Text = "Save"
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_qr_scanner,
                title = "QR Tools",
                subtitle = "Smart Scan Page & Share QR",
                description = "Scan any QR codes visible on webpages instantly using our Google Lens-style scanner, or generate live sharing QR codes for links in real-time.",
                accentColor = Color(0xFF00D2C4),
                badge1Icon = Icons.Rounded.CameraAlt,
                badge1Text = "Scan",
                badge2Icon = Icons.Rounded.Share,
                badge2Text = "Share"
            ),
            OnboardingPageData(
                imageRes = R.drawable.ob_extensions_vault,
                title = "Extensions & Vault",
                subtitle = "Firefox Extensions & Secure Locker",
                description = "Install desktop extensions (like uBlock Origin) to block ads, and lock your sensitive downloads behind biometrically encrypted local vault storage.",
                accentColor = Color(0xFFFF3B5C),
                badge1Icon = Icons.Rounded.Extension,
                badge1Text = "uBlock",
                badge2Icon = Icons.Rounded.Lock,
                badge2Text = "Vault"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    // Floating animation offset variables
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse
        ), label = "f1"
    )
    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ), label = "f2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgStart, BgEnd)
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
                    color = DarkSlate.copy(alpha = 0.6f),
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
                .padding(top = 48.dp, bottom = 100.dp),
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
                    // Illustration Card Showcase Area (Soft white circle background + Floating animated badges)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background Soft White Circle Card
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.6f))
                                .border(1.5.dp, Color.White, CircleShape)
                        )

                        // Central main illustration
                        Image(
                            painter = painterResource(id = pageData.imageRes),
                            contentDescription = pageData.title,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Floating Badge 1 ( Bobbing offset 1 )
                        FloatingFeatureBadge(
                            icon = pageData.badge1Icon,
                            containerColor = pageData.accentColor,
                            text = pageData.badge1Text,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 16.dp, y = (32f + floatOffset1).dp)
                        )

                        // Floating Badge 2 ( Bobbing offset 2 )
                        FloatingFeatureBadge(
                            icon = pageData.badge2Icon,
                            containerColor = DarkSlate,
                            text = pageData.badge2Text,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-16).dp, y = ((-32f) + floatOffset2).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

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
                            color = DarkSlate,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
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
                            color = SubtextColor,
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
                        targetValue = if (active) pages[pagerState.currentPage].accentColor else DarkSlate.copy(alpha = 0.15f),
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
                    .height(56.dp)
                    .shadow(8.dp, shape = CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSlate,
                    contentColor = Color.White
                ),
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
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

@Composable
private fun FloatingFeatureBadge(
    icon: ImageVector,
    containerColor: Color,
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(6.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
