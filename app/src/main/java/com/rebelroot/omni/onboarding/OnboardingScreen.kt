/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rebelroot.omni.utils.RoleManagerHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rebelroot.omni.R
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ── Minimal Matte Design Tokens ─────────────────────────────────────────────
// Clean, simple matte surfaces with subtle neutral slate tones — no gloss, no vibrant cyan/purple.
private val SurfaceLight     = Color(0xFFF8F9FA)
private val SurfaceDark      = Color(0xFF121316)
private val CardLight        = Color(0xFFFFFFFF)
private val CardDark         = Color(0xFF1E2024)
private val TitleLight       = Color(0xFF1E293B)
private val TitleDark        = Color(0xFFF1F5F9)
private val BodyLight        = Color(0xFF475569)
private val BodyDark         = Color(0xFF94A3B8)
private val CreamyBg         = Color(0xFFFAF8F5)
private val MatteSlateAccent = Color(0xFF475569)
private val MatteSlateDarkAccent = Color(0xFF94A3B8)

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
    var selectedNavbarPos   by remember { mutableStateOf(viewModel.addressBarPosition) }
    var autoCycleNavbar     by remember { mutableStateOf(true) }
    var selectedSearchEngine by remember { mutableStateOf(viewModel.selectedSearchEngine) }
    var isDarkTheme         by remember { mutableStateOf(viewModel.isDarkThemeEnabled) }
    var isCreamyTheme       by remember { mutableStateOf(viewModel.isCreamyMode) }
    var selectedProxy       by remember { mutableStateOf(viewModel.proxyProvider) }

    val pages = remember(context) { buildPages(context) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage   = pagerState.currentPage == pages.size - 1
    val currentPage  = pages[pagerState.currentPage]
    val accentColor  = currentPage.accentColor

    val bg = when {
        isDarkTheme   -> SurfaceDark
        isCreamyTheme -> CreamyBg
        else          -> SurfaceLight
    }
    val titleColor = if (isDarkTheme) TitleDark else TitleLight
    val bodyColor  = if (isDarkTheme) BodyDark  else BodyLight
    val cardColor  = if (isDarkTheme) CardDark  else CardLight

    // Auto-cycle navbar on that page
    LaunchedEffect(pagerState.currentPage, autoCycleNavbar) {
        if (pages[pagerState.currentPage].id == "navbar_position" && autoCycleNavbar) {
            val positions = listOf("Split", "Top", "Bottom")
            var idx = positions.indexOf(selectedNavbarPos).coerceAtLeast(0)
            while (autoCycleNavbar && pagerState.currentPage == pages.indexOfFirst { it.id == "navbar_position" }) {
                delay(3200)
                idx = (idx + 1) % positions.size
                selectedNavbarPos = positions[idx]
                viewModel.saveAddressBarPosition(context, positions[idx])
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step label — small tonal chip, no border
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step, pagerState.currentPage + 1, pages.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            if (!isLastPage) {
                TextButton(
                    onClick = {
                        viewModel.saveOnboardingCompleted(context, true)
                        onFinish()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = bodyColor)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Pager ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 86.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { page ->
                val pageData = pages[page]
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (pageOffset.absoluteValue * 0.04f).coerceIn(0f, 0.04f)
                val alpha = 1f - (pageOffset.absoluteValue * 0.25f).coerceIn(0f, 0.25f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    PageContent(
                        pageData = pageData,
                        isDarkTheme = isDarkTheme,
                        isCreamyTheme = isCreamyTheme,
                        selectedNavbarPos = selectedNavbarPos,
                        selectedSearchEngine = selectedSearchEngine,
                        selectedProxy = selectedProxy,
                        titleColor = titleColor,
                        bodyColor = bodyColor,
                        cardColor = cardColor,
                        onThemeChange = { dark, creamy ->
                            isDarkTheme = dark; isCreamyTheme = creamy
                            viewModel.saveDarkTheme(context, dark)
                            viewModel.saveCreamyMode(context, creamy)
                        },
                        onSearchEngineChange = { engine ->
                            selectedSearchEngine = engine
                            viewModel.saveSearchEngine(context, engine)
                        },
                        onNavbarChange = { pos ->
                            autoCycleNavbar = false
                            selectedNavbarPos = pos
                            viewModel.saveAddressBarPosition(context, pos)
                            Toast.makeText(context, context.getString(R.string.onboarding_layout_toast, pos), Toast.LENGTH_SHORT).show()
                        },
                        onProxyChange = { provider ->
                            selectedProxy = provider
                            viewModel.saveProxyProvider(context, provider)
                        },
                        onInstallUblock = {
                            viewModel.installExtensionFromUrl(
                                "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon-607454-latest.xpi", context
                            )
                        },
                        onSetDefault = { openDefaultBrowserSettings(context) },
                        context = context
                    )
                }
            }
        }

        // ── Bottom nav ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Pill dots
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isActive) 20.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                        label = "dot_$index"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isActive) pages[pagerState.currentPage].accentColor
                                      else if (isDarkTheme) Color(0xFF3D4043) else Color(0xFFDADCE0),
                        animationSpec = tween(200), label = "dot_c_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // Next / Finish button — filled tonal, no elevation
            FilledTonalButton(
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
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accentColor,
                    contentColor = Color.White
                ),
                shape = CircleShape,
                elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = if (isLastPage) stringResource(R.string.onboarding_start_browsing)
                           else stringResource(R.string.onboarding_next),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isLastPage) Icons.Rounded.CheckCircle
                                  else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Page content dispatcher ───────────────────────────────────────────────────

@Composable
private fun PageContent(
    pageData: OnboardingPage,
    isDarkTheme: Boolean,
    isCreamyTheme: Boolean,
    selectedNavbarPos: String,
    selectedSearchEngine: String,
    selectedProxy: String,
    titleColor: Color,
    bodyColor: Color,
    cardColor: Color,
    onThemeChange: (Boolean, Boolean) -> Unit,
    onSearchEngineChange: (String) -> Unit,
    onNavbarChange: (String) -> Unit,
    onProxyChange: (String) -> Unit,
    onInstallUblock: () -> Unit,
    onSetDefault: () -> Unit,
    context: Context
) {
    // Image / illustration — prominent full-size mockup
    if (pageData.id == "navbar_position") {
        AnimatedNavbarShowcase(
            selectedPos = selectedNavbarPos,
            modifier = Modifier.height(280.dp).fillMaxWidth()
        )
    } else {
        val imageRes = if (pageData.id == "theme_setup") {
            if (isDarkTheme) R.drawable.ob_theme_dark else R.drawable.ob_theme_light
        } else pageData.imageRes

        PhoneMockup(
            imageRes = imageRes,
            modifier = Modifier.height(210.dp).wrapContentWidth()
        )
    }

    Spacer(Modifier.height(16.dp))

    // Title
    Text(
        text = pageData.title,
        color = titleColor,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        lineHeight = 30.sp
    )

    Spacer(Modifier.height(6.dp))

    // Subtitle tagline — plain text, no chip border
    Text(
        text = when (pageData.id) {
            "navbar_position" -> stringResource(R.string.onboarding_active_layout, selectedNavbarPos)
            "search_setup"    -> stringResource(R.string.onboarding_selected_search, selectedSearchEngine)
            "theme_setup"     -> when {
                isDarkTheme   -> stringResource(R.string.onboarding_tagline_dark_active)
                isCreamyTheme -> stringResource(R.string.onboarding_tagline_creamy_active)
                else          -> stringResource(R.string.onboarding_tagline_pure_active)
            }
            else -> pageData.tagline
        },
        color = bodyColor,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )

    Spacer(Modifier.height(20.dp))

    // Interactive pickers
    when (pageData.id) {
        "theme_setup"      -> ThemePicker(isDarkTheme, isCreamyTheme, titleColor, bodyColor, cardColor, onThemeChange)
        "search_setup"     -> SearchPicker(selectedSearchEngine, isDarkTheme, titleColor, bodyColor, cardColor, onSearchEngineChange)
        "navbar_position"  -> NavbarPicker(selectedNavbarPos, isDarkTheme, titleColor, cardColor, onNavbarChange, context)
        "proxy_hub"        -> ProxyHubContent(selectedProxy, isDarkTheme, titleColor, bodyColor, cardColor, onProxyChange)
        "extensions"       -> ExtensionsContent(isDarkTheme, titleColor, bodyColor, cardColor, pageData, onInstallUblock)
        "default_browser"  -> DefaultBrowserContent(isDarkTheme, titleColor, bodyColor, cardColor, pageData, onSetDefault)
        else -> {
            // Standard feature list
            if (pageData.features.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pageData.features.forEach { feature ->
                        FeatureRow(feature, pageData.accentColor, isDarkTheme, titleColor, bodyColor, cardColor)
                    }
                }
            }
        }
    }
}

// ── Theme Picker ──────────────────────────────────────────────────────────────

@Composable
private fun ThemePicker(
    isDarkTheme: Boolean, isCreamyTheme: Boolean,
    titleColor: Color, bodyColor: Color, cardColor: Color,
    onThemeChange: (Boolean, Boolean) -> Unit
) {
    val options = listOf(
        Triple(stringResource(R.string.onboarding_theme_dark_slate), true, false),
        Triple(stringResource(R.string.onboarding_theme_creamy_light), false, true),
        Triple(stringResource(R.string.onboarding_theme_pure_light), false, false)
    )
    val icons = listOf(Icons.Rounded.DarkMode, Icons.Rounded.FilterVintage, Icons.Rounded.LightMode)
    val accent = Color(0xFF818CF8)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.onboarding_theme_selection_title),
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = titleColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, (label, darkVal, creamyVal) ->
                    val isSelected = isDarkTheme == darkVal && isCreamyTheme == creamyVal
                    Surface(
                        onClick = { onThemeChange(darkVal, creamyVal) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accent else accent.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = icons[i], contentDescription = null,
                                tint = if (isSelected) Color.White else accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label, fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else accent,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search Engine Picker ──────────────────────────────────────────────────────

@Composable
private fun SearchPicker(
    selectedEngine: String, isDarkTheme: Boolean,
    titleColor: Color, bodyColor: Color, cardColor: Color,
    onSelect: (String) -> Unit
) {
    val accent = Color(0xFF10B981)
    val engines = listOf("Google", "DuckDuckGo", "Brave", "Bing", "Yahoo", "Yandex", "Ecosia", "Startpage", "Qwant")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.onboarding_search_selection_title),
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = titleColor
            )
            engines.forEach { engine ->
                val isSelected = selectedEngine == engine
                Surface(
                    onClick = { onSelect(engine) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) accent else accent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Search, null,
                                tint = if (isSelected) Color.White else accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = engine, fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color.White else titleColor
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Navbar Picker ─────────────────────────────────────────────────────────────

@Composable
private fun NavbarPicker(
    selectedPos: String, isDarkTheme: Boolean,
    titleColor: Color, cardColor: Color,
    onSelect: (String) -> Unit, context: Context
) {
    val accent = Color(0xFF6366F1)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.onboarding_navbar_selection_title),
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = titleColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Split", "Top", "Bottom").forEach { pos ->
                    val isSelected = selectedPos == pos
                    val label = when (pos) {
                        "Split"  -> stringResource(R.string.onboarding_nav_split)
                        "Top"    -> stringResource(R.string.onboarding_nav_top)
                        else     -> stringResource(R.string.onboarding_nav_bottom)
                    }
                    val icon = when (pos) {
                        "Split"  -> Icons.Rounded.ViewAgenda
                        "Top"    -> Icons.Rounded.VerticalAlignTop
                        else     -> Icons.Rounded.VerticalAlignBottom
                    }
                    Surface(
                        onClick = { onSelect(pos) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accent else accent.copy(alpha = 0.08f),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                icon, null,
                                tint = if (isSelected) Color.White else accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label, fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else accent
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Extensions page content ───────────────────────────────────────────────────

@Composable
private fun ExtensionsContent(
    isDarkTheme: Boolean, titleColor: Color, bodyColor: Color, cardColor: Color,
    pageData: OnboardingPage, onInstallUblock: () -> Unit
) {
    val accent = pageData.accentColor

    // uBlock install card — elevated, no outline
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.Shield, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_install_ublock_title),
                    color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.onboarding_install_ublock_desc),
                    color = bodyColor, fontSize = 12.sp
                )
            }
            FilledTonalButton(
                onClick = onInstallUblock,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accent, contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.onboarding_install_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Feature list
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pageData.features.forEach { feature ->
            FeatureRow(feature, accent, isDarkTheme, titleColor, bodyColor, cardColor)
        }
    }
}

// ── Default browser page content ─────────────────────────────────────────────

@Composable
private fun DefaultBrowserContent(
    isDarkTheme: Boolean, titleColor: Color, bodyColor: Color, cardColor: Color,
    pageData: OnboardingPage, onSetDefault: () -> Unit
) {
    val accent = pageData.accentColor

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_default_card_title),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.onboarding_default_card_desc),
                fontSize = 13.sp, color = bodyColor, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            val btnContainerColor = if (isDarkTheme) Color(0xFF475569) else Color(0xFF334155)
            Button(
                onClick = onSetDefault,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnContainerColor,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.onboarding_set_default_button),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pageData.features.forEach { feature ->
            FeatureRow(feature, accent, isDarkTheme, titleColor, bodyColor, cardColor)
        }
    }
}

// ── Feature row — clean tonal card, no outline ────────────────────────────────

@Composable
private fun FeatureRow(
    feature: FeatureItem,
    accentColor: Color,
    isDark: Boolean,
    titleColor: Color,
    bodyColor: Color,
    cardColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(feature.icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    color = titleColor, fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 18.sp
                )
                Text(
                    text = feature.detail,
                    color = bodyColor, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
        }
    }
}

// ── Phone mockup — clean dark frame, no heavy border ─────────────────────────

@Composable
private fun PhoneMockup(imageRes: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF1A1C1E),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxHeight().aspectRatio(0.46f)
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.FillBounds,
                alignment = Alignment.TopCenter
            )
        }
    }
}

// ── Animated Navbar Showcase ──────────────────────────────────────────────────

@Composable
private fun AnimatedNavbarShowcase(selectedPos: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1C1E),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxHeight().aspectRatio(0.56f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1C1E))
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41", color = Color(0xFF9BA2AB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.width(24.dp).height(3.dp).clip(CircleShape).background(Color(0xFF3D4043)))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SignalCellular4Bar, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(10.dp))
                        Icon(Icons.Rounded.BatteryFull,       null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(10.dp))
                    }
                }
                // Page canvas
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF8F9FA))
                ) {
                    // Top bar
                    Box(modifier = Modifier.align(Alignment.TopCenter)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedPos == "Split" || selectedPos == "Top",
                            enter = slideInVertically(initialOffsetY = { -it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
                            exit  = slideOutVertically(targetOffsetY  = { -it }, animationSpec = tween(250)) + fadeOut()
                        ) {
                            if (selectedPos == "Split") SplitTopBar() else FullTopBar()
                        }
                    }
                    // Bottom bar
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selectedPos == "Split" || selectedPos == "Bottom",
                            enter = slideInVertically(initialOffsetY = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
                            exit  = slideOutVertically(targetOffsetY  = { it }, animationSpec = tween(250)) + fadeOut()
                        ) {
                            if (selectedPos == "Split") SplitBottomBar() else FullBottomBar()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SplitTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .background(Color(0xFF1A1C1E)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.Home, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(16.dp))
        Row(
            modifier = Modifier.weight(1f).height(30.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2D30)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
            Text("rebelroot.xyz", color = Color(0xFFE3E2E6), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.StarBorder, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(12.dp))
        }
    }
}

@Composable private fun FullTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .background(Color(0xFF1A1C1E)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.Home, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(15.dp))
        Row(
            modifier = Modifier.weight(1f).height(28.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2D30)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
            Text("rebelroot.xyz", color = Color(0xFFE3E2E6), fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
        }
        TabCountBadge(boxSize = 19.dp)
        Icon(Icons.Rounded.Menu, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(15.dp))
    }
}

@Composable private fun SplitBottomBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .background(Color(0xFF1A1C1E)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack,    null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(16.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(16.dp))
        Icon(Icons.Rounded.GridView, null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
        TabCountBadge(boxSize = 20.dp)
        Icon(Icons.Rounded.Menu, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(16.dp))
    }
}

@Composable private fun FullBottomBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .background(Color(0xFF1A1C1E)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Rounded.Home, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(15.dp))
        Row(
            modifier = Modifier.weight(1f).height(28.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2D30)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Rounded.Lock, null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
            Text("rebelroot.xyz", color = Color(0xFFE3E2E6), fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
        }
        TabCountBadge(boxSize = 19.dp)
        Icon(Icons.Rounded.Menu, null, tint = Color(0xFF9BA2AB), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun TabCountBadge(
    count: String = "1",
    modifier: Modifier = Modifier,
    boxSize: androidx.compose.ui.unit.Dp = 20.dp
) {
    Box(
        modifier = modifier.size(boxSize)
            .border(1.dp, Color(0xFF9BA2AB), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count, color = Color(0xFFE3E2E6),
            fontSize = (boxSize.value * 0.48f).sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ── Pages data ────────────────────────────────────────────────────────────────

private fun buildPages(context: Context): List<OnboardingPage> {
    val matteAccent = Color(0xFF475569)
    return listOf(
        OnboardingPage(
            id = "browse",
            imageRes = R.drawable.ob_secure_browser,
            title = context.getString(R.string.onboarding_title_browse),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_browse),
            features = listOf(
                FeatureItem(Icons.Rounded.Search,        context.getString(R.string.onboarding_feature_smart_bar_title),  context.getString(R.string.onboarding_feature_smart_bar_desc)),
                FeatureItem(Icons.Rounded.Newspaper,     context.getString(R.string.onboarding_feature_discover_title),   context.getString(R.string.onboarding_feature_discover_desc)),
                FeatureItem(Icons.Rounded.VisibilityOff, context.getString(R.string.onboarding_feature_incognito_title),  context.getString(R.string.onboarding_feature_incognito_desc)),
                FeatureItem(Icons.Rounded.ContentCopy,   context.getString(R.string.onboarding_feature_copy_title),       context.getString(R.string.onboarding_feature_copy_desc))
            )
        ),
        OnboardingPage(
            id = "theme_setup",
            imageRes = R.drawable.ob_quick_tools,
            title = context.getString(R.string.onboarding_title_theme),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_theme),
            features = emptyList()
        ),
        OnboardingPage(
            id = "extensions",
            imageRes = R.drawable.ob_extensions_vault,
            title = context.getString(R.string.onboarding_title_extensions),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_extensions),
            features = listOf(
                FeatureItem(Icons.Rounded.Shield,           context.getString(R.string.onboarding_feature_ublock_title),      context.getString(R.string.onboarding_feature_ublock_desc)),
                FeatureItem(Icons.Rounded.AddCircleOutline, context.getString(R.string.onboarding_feature_store_title),       context.getString(R.string.onboarding_feature_store_desc)),
                FeatureItem(Icons.Rounded.Block,            context.getString(R.string.onboarding_feature_ai_blocker_title),  context.getString(R.string.onboarding_feature_ai_blocker_desc)),
                FeatureItem(Icons.Rounded.Lock,             context.getString(R.string.onboarding_feature_vault_title),       context.getString(R.string.onboarding_feature_vault_desc))
            )
        ),
        OnboardingPage(
            id = "search_setup",
            imageRes = R.drawable.ob_search_selector,
            title = context.getString(R.string.onboarding_title_search),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_search),
            features = emptyList()
        ),
        OnboardingPage(
            id = "navbar_position",
            imageRes = R.drawable.ob_nav_split,
            title = context.getString(R.string.onboarding_title_navbar),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_navbar),
            features = emptyList()
        ),
        OnboardingPage(
            id = "proxy_hub",
            imageRes = R.drawable.ob_secure_browser,
            title = context.getString(R.string.onboarding_title_proxy),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_proxy),
            features = emptyList()
        ),
        OnboardingPage(
            id = "default_browser",
            imageRes = R.drawable.ob_secure_browser,
            title = context.getString(R.string.onboarding_title_default_browser),
            accentColor = matteAccent,
            tagline = context.getString(R.string.onboarding_tagline_default_browser),
            features = listOf(
                FeatureItem(Icons.Rounded.Shield, context.getString(R.string.onboarding_feature_auto_protection_title), context.getString(R.string.onboarding_feature_auto_protection_desc)),
                FeatureItem(Icons.Rounded.Bolt,   context.getString(R.string.onboarding_feature_lightning_title),       context.getString(R.string.onboarding_feature_lightning_desc)),
                FeatureItem(Icons.Rounded.Lock,   context.getString(R.string.onboarding_feature_encrypted_title),       context.getString(R.string.onboarding_feature_encrypted_desc))
            )
        )
    )
}

// ── Default browser intent ────────────────────────────────────────────────────

private fun openDefaultBrowserSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (RoleManagerHelper.openDefaultBrowserRole(context)) return
        }
        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }
}

// ── Proxy Hub onboarding ──────────────────────────────────────────────────────

private data class ProxyOption(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val badge: String?,           // "Recommended", "Private", etc.
    val badgeColor: Color
)

@Composable
private fun ProxyHubContent(
    selectedProxy: String,
    isDarkTheme: Boolean,
    titleColor: Color,
    bodyColor: Color,
    cardColor: Color,
    onSelect: (String) -> Unit
) {
    val accent = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val badgeColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)

    val options = listOf(
        ProxyOption(
            id = "direct",
            icon = Icons.Rounded.Speed,
            title = stringResource(R.string.onboarding_proxy_direct_title),
            subtitle = stringResource(R.string.onboarding_proxy_direct_desc),
            badge = stringResource(R.string.onboarding_proxy_badge_recommended),
            badgeColor = badgeColor
        ),
        ProxyOption(
            id = "tor_builtin",
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.onboarding_proxy_tor_title),
            subtitle = stringResource(R.string.onboarding_proxy_tor_desc),
            badge = stringResource(R.string.onboarding_proxy_badge_private),
            badgeColor = badgeColor
        ),
        ProxyOption(
            id = "custom_proxy",
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.onboarding_proxy_custom_title),
            subtitle = stringResource(R.string.onboarding_proxy_custom_desc),
            badge = stringResource(R.string.onboarding_proxy_badge_advanced),
            badgeColor = badgeColor
        )
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_proxy_section_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = titleColor
            )
            Text(
                text = stringResource(R.string.onboarding_proxy_section_desc),
                fontSize = 12.sp,
                color = bodyColor,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(2.dp))

            options.forEach { option ->
                val isSelected = selectedProxy == option.id
                Surface(
                    onClick = { onSelect(option.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent,
                    tonalElevation = 0.dp,
                    border = if (isSelected)
                        androidx.compose.foundation.BorderStroke(1.5.dp, accent)
                    else
                        androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Icon container
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    option.icon, null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = option.title,
                                    color = titleColor,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (option.badge != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = option.badgeColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = option.badge,
                                            color = option.badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = option.subtitle,
                                color = bodyColor,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle, null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_proxy_footer),
                fontSize = 11.sp,
                color = bodyColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
