package com.rebelroot.omni.browser

import android.app.Activity
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import com.rebelroot.omni.media.MediaInterceptor
import com.rebelroot.omni.privacy.FireButton

@Composable
fun TabItem(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .height(32.dp)
            .widthIn(max = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    0.5.dp,
                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Tab",
                    modifier = Modifier.size(8.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun HomeScreenContent(
    viewModel: BrowserViewModel,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLocker: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenExtensions: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A0F)) // Obsidian black background
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center branding OMNI stylized logo Image
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.rebelroot.omni.R.drawable.omni_home_logo),
            contentDescription = "Omni Browser Logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp) // Adjusted height for visibility
                .padding(horizontal = 16.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        // Flat Slate search pill
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search or type web address", color = Color(0xFF8E9AA8), fontSize = 14.sp) },
            leadingIcon = {
                // Colored G icon on a white circle background
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "G",
                        color = Color(0xFF0088FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice Search",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (searchText.isNotEmpty()) {
                        onNavigateTo(searchText)
                    }
                }
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF16222F),
                unfocusedBorderColor = Color(0xFF16222F),
                focusedContainerColor = Color(0xFF16222F),
                unfocusedContainerColor = Color(0xFF16222F)
            )
        )

        // Shortcut Button Grid (2 rows of 4)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShortcutItem(title = "Twitter", icon = Icons.Rounded.Public, onClick = { onNavigateTo("https://twitter.com") })
                ShortcutItem(title = "Unsplash", icon = Icons.Rounded.CameraAlt, onClick = { onNavigateTo("https://unsplash.com") })
                ShortcutItem(title = "Pinterest", icon = Icons.Rounded.Image, onClick = { onNavigateTo("https://pinterest.com") })
                ShortcutItem(title = "Downloads", icon = Icons.Rounded.Download, isAccented = true, onClick = onOpenDownloads)
            }
            
            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShortcutItem(title = "History", icon = Icons.Rounded.History, onClick = onOpenHistory)
                ShortcutItem(title = "Bookmarks", icon = Icons.Rounded.Bookmark, onClick = {
                    Toast.makeText(context, "Bookmarks", Toast.LENGTH_SHORT).show()
                })
                ShortcutItem(
                    title = "Incognito",
                    icon = if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    onClick = { viewModel.toggleIncognitoMode(context) }
                )
                ShortcutItem(title = "Add", icon = Icons.Rounded.Add, onClick = {
                    viewModel.createNewTab(context, "about:blank")
                })
            }
        }

        // Quick Tools Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Tools",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    ToolCard(
                        title = "Doc Scanner",
                        subtitle = "ML Scanner",
                        icon = Icons.Rounded.DocumentScanner,
                        onClick = onOpenScanner
                    )
                }
                item {
                    ToolCard(
                        title = "QR Tools",
                        subtitle = "Scan & Gen",
                        icon = Icons.Rounded.QrCodeScanner,
                        onClick = onOpenQrTools
                    )
                }
                item {
                    ToolCard(
                        title = "Safe Locker",
                        subtitle = "Secure Vault",
                        icon = Icons.Rounded.Lock,
                        onClick = onOpenLocker
                    )
                }
                item {
                    ToolCard(
                        title = "Extensions",
                        subtitle = "Web Addons",
                        icon = Icons.Rounded.Extension,
                        onClick = onOpenExtensions
                    )
                }
            }
        }

        // Discover Section Block
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discover",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See all",
                    color = Color(0xFF0088FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Loading feed...", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                color = Color(0xFF16222F),
                border = BorderStroke(0.5.dp, Color(0xFF23374A))
            ) {
                Column {
                    DiscoverRowItem(
                        icon = Icons.Rounded.Article,
                        title = "The Future of Web Browsing",
                        subtitle = "Trending now",
                        onClick = { onNavigateTo("https://nova.app/blog/future-web") }
                    )
                    HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    DiscoverRowItem(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = "Top Stories Today",
                        subtitle = "Curated for you",
                        onClick = { onNavigateTo("https://news.google.com") }
                    )
                }
            }
        }
    }
}

@Composable
fun ShortcutItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isAccented: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() },
            color = if (isAccented) Color(0xFF0066FF) else Color(0xFF16222F),
            border = if (isAccented) null else BorderStroke(0.5.dp, Color(0xFF23374A))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = title,
            color = Color(0xFF8E9AA8),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DiscoverRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF243647)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0088FF),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF8E9AA8),
                fontSize = 11.sp
            )
        }
        
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF8E9AA8)
        )
    }
}

@Composable
fun ToolCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = Color(0xFF16222F),
        border = BorderStroke(0.5.dp, Color(0xFF23374A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF243647)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF0088FF),
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF8E9AA8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenLocker: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onPlayOnlineStream: (String, String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    
    var showMenu by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(viewModel.currentUrl) }
    var isInputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    // Video detection states
    val detectedMedia by viewModel.mediaInterceptor.detectedMedia.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var selectedMediaItem by remember { mutableStateOf<MediaInterceptor.DetectedMedia?>(null) }
    var showExtensionsSheet by remember { mutableStateOf(false) }

    // Fullscreen download overlay — hoisted outside the if-block so state survives
    // fullscreen entry/exit transitions and doesn't reset on every recomposition.
    var showFullscreenDownloadBtn by remember { mutableStateOf(true) }

    // Auto-fade after 3 s while playing; reappear immediately on pause
    LaunchedEffect(showFullscreenDownloadBtn, viewModel.isVideoPlayingInPage) {
        if (showFullscreenDownloadBtn && viewModel.isVideoPlayingInPage) {
            kotlinx.coroutines.delay(3000)
            showFullscreenDownloadBtn = false
        }
    }
    LaunchedEffect(viewModel.isVideoPlayingInPage) {
        if (!viewModel.isVideoPlayingInPage) {
            showFullscreenDownloadBtn = true
        }
    }
    // Reset visibility whenever fullscreen is entered fresh
    LaunchedEffect(viewModel.isFullscreen) {
        if (viewModel.isFullscreen) showFullscreenDownloadBtn = true
    }

    // Offline Translation states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var translationSourceText by remember { mutableStateOf("") }
    var translationResultText by remember { mutableStateOf("") }
    var translationProgress by remember { mutableStateOf(false) }

    // Tab Switcher states
    var showTabGroupsSheet by remember { mutableStateOf(false) }
    
    // Developer Console state
    var showConsoleSheet by remember { mutableStateOf(false) }

    // Tools sheet state
    var showToolsSheet by remember { mutableStateOf(false) }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        val request = viewModel.activeSystemPermissionRequest
        if (request != null) {
            if (allGranted) {
                request.onGranted()
            } else {
                request.onDenied()
            }
            viewModel.clearActiveSystemPermissionRequest()
        }
    }

    LaunchedEffect(viewModel.activeSystemPermissionRequest) {
        viewModel.activeSystemPermissionRequest?.let { request ->
            systemPermissionLauncher.launch(request.permissions ?: emptyArray())
        }
    }

    LaunchedEffect(viewModel.currentUrl) {
        inputUrl = viewModel.currentUrl
    }

    LaunchedEffect(viewModel.isFullscreen) {
        val activity = context as? Activity
        activity?.let {
            FullscreenManager.setFullscreen(it, viewModel.isFullscreen)
        }
    }

    LaunchedEffect(viewModel, onPlayOnlineStream) {
        viewModel.onPlayVideoRequestReceived = { url, pageUrl ->
            onPlayOnlineStream(url, pageUrl)
        }
        viewModel.pendingVideoUrl?.let { url ->
            viewModel.pendingVideoUrl = null
            onPlayOnlineStream(url, url)
        }
    }

    val showHomeScreen = viewModel.currentUrl == "about:blank" || viewModel.currentUrl.isEmpty()

    // Add back gesture handler to handle system back clicks safely
    androidx.activity.compose.BackHandler(enabled = !showHomeScreen && viewModel.canGoBack) {
        viewModel.goBack()
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !viewModel.isFullscreen && !showHomeScreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .border(
                                BorderStroke(
                                    width = 0.5.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                            ),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shadowElevation = 0.dp
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(visible = !isInputFocused) {
                                    IconButton(
                                        onClick = { viewModel.goBack() },
                                        enabled = viewModel.canGoBack,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (viewModel.canGoBack) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = !isInputFocused) {
                                    IconButton(
                                        onClick = { viewModel.goForward() },
                                        enabled = viewModel.canGoForward,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Forward",
                                            tint = if (viewModel.canGoForward) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = if (inputUrl == "about:blank") "" else inputUrl,
                                    onValueChange = { inputUrl = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { isInputFocused = it.isFocused },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            viewModel.loadUrl(inputUrl)
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Search icon",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    },
                                    trailingIcon = {
                                        if (inputUrl.isNotEmpty() && inputUrl != "about:blank") {
                                            IconButton(
                                                onClick = { inputUrl = "" },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )

                                AnimatedVisibility(visible = isInputFocused) {
                                    TextButton(
                                        onClick = {
                                            inputUrl = viewModel.currentUrl
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    ) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                    }
                                }

                                AnimatedVisibility(visible = !isInputFocused) {
                                    IconButton(
                                        onClick = { viewModel.reload() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Reload",
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = !isInputFocused) {
                                    IconButton(
                                        onClick = { showExtensionsSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Extension,
                                            contentDescription = "Extensions",
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }

                            if (viewModel.isLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !viewModel.isFullscreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // Flat minimal bottom bar persisting exactly as requested in screenshots
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0D1620),
                    border = BorderStroke(0.5.dp, Color(0xFF16222F).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Tabs counter
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
                                .clickable { showTabGroupsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.tabs.size.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Tools
                        IconButton(onClick = { showToolsSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = "Tools",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->

    // ── Edge-style Grid Menu Bottom Sheet ──────────────────────────────
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0D1620),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF3A4A5A))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ── Row 1: Primary actions (colored, filled icons) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Bookmarks
                    MenuGridCell(
                        icon = Icons.Rounded.Bookmark,
                        label = "Bookmarks",
                        iconTint = Color(0xFF0088FF),
                        iconBg = Color(0xFF0088FF).copy(alpha = 0.12f),
                        onClick = {
                            showMenu = false
                            val activeTabTitle = viewModel.tabs.find { it.id == viewModel.activeTabId }?.title ?: "Bookmark"
                            viewModel.addToHistory(activeTabTitle, viewModel.currentUrl)
                            Toast.makeText(context, "Added to Bookmarks", Toast.LENGTH_SHORT).show()
                        }
                    )
                    // History
                    MenuGridCell(
                        icon = Icons.Rounded.History,
                        label = "History",
                        iconTint = Color(0xFF0088FF),
                        iconBg = Color(0xFF0088FF).copy(alpha = 0.12f),
                        onClick = { showMenu = false; onOpenHistory() }
                    )
                    // Downloads
                    MenuGridCell(
                        icon = Icons.Rounded.Download,
                        label = "Downloads",
                        iconTint = Color(0xFF0088FF),
                        iconBg = Color(0xFF0088FF).copy(alpha = 0.12f),
                        onClick = { showMenu = false; onOpenDownloads() }
                    )
                    // Settings
                    MenuGridCell(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        iconTint = Color(0xFF0088FF),
                        iconBg = Color(0xFF0088FF).copy(alpha = 0.12f),
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF23374A).copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // ── Row 2: Secondary actions ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // New Tab
                    MenuGridCell(
                        icon = Icons.Rounded.Add,
                        label = "New Tab",
                        iconTint = Color(0xFFAABBCC),
                        iconBg = Color(0xFF1A2A3A),
                        onClick = { showMenu = false; viewModel.createNewTab(context, "about:blank") }
                    )
                    // Incognito (with toggle state)
                    MenuGridCell(
                        icon = if (viewModel.isIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        label = "Incognito",
                        iconTint = if (viewModel.isIncognitoMode) Color(0xFF0088FF) else Color(0xFFAABBCC),
                        iconBg = if (viewModel.isIncognitoMode) Color(0xFF0088FF).copy(alpha = 0.15f) else Color(0xFF1A2A3A),
                        onClick = { viewModel.toggleIncognitoMode(context) }
                    )
                    // Reload
                    MenuGridCell(
                        icon = Icons.Rounded.Refresh,
                        label = "Reload",
                        iconTint = Color(0xFFAABBCC),
                        iconBg = Color(0xFF1A2A3A),
                        onClick = { showMenu = false; viewModel.reload() }
                    )
                    // Native Player
                    MenuGridCell(
                        icon = Icons.Rounded.PlayCircle,
                        label = "Native\nPlayer",
                        iconTint = Color(0xFFAABBCC),
                        iconBg = Color(0xFF1A2A3A),
                        onClick = {
                            showMenu = false
                            val activeTabUrl = viewModel.currentUrl
                            if (activeTabUrl.isNotEmpty() && activeTabUrl != "about:blank") {
                                viewModel.onPlayVideoRequestReceived?.invoke(activeTabUrl, activeTabUrl)
                            } else {
                                Toast.makeText(context, "No active video stream to play", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Row 3: Power tools ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Desktop Site
                    MenuGridCell(
                        icon = Icons.Rounded.Computer,
                        label = "Desktop\nSite",
                        iconTint = if (viewModel.isDesktopMode) Color(0xFF0088FF) else Color(0xFFAABBCC),
                        iconBg = if (viewModel.isDesktopMode) Color(0xFF0088FF).copy(alpha = 0.15f) else Color(0xFF1A2A3A),
                        onClick = { viewModel.toggleDesktopMode(context) }
                    )
                    // Extensions
                    MenuGridCell(
                        icon = Icons.Rounded.Extension,
                        label = "Extensions",
                        iconTint = Color(0xFFAABBCC),
                        iconBg = Color(0xFF1A2A3A),
                        onClick = { showMenu = false; showExtensionsSheet = true }
                    )
                    // Reader Mode
                    MenuGridCell(
                        icon = Icons.Rounded.Book,
                        label = "Reader\nMode",
                        iconTint = Color(0xFFAABBCC),
                        iconBg = Color(0xFF1A2A3A),
                        onClick = {
                            showMenu = false
                            try { viewModel.toggleReaderMode() } catch (e: Exception) {
                                Toast.makeText(context, "Reader Mode not supported on this page", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    // Burn Data
                    MenuGridCell(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Burn\nData",
                        iconTint = Color(0xFFFF4444),
                        iconBg = Color(0xFFFF4444).copy(alpha = 0.12f),
                        onClick = {
                            showMenu = false
                            coroutineScope.launch {
                                val runtime = viewModel.getGeckoRuntime(context)
                                FireButton(runtime, context).burn()
                                Toast.makeText(context, "🔥 Browsing data burned", Toast.LENGTH_SHORT).show()
                                viewModel.loadUrl("about:blank")
                            }
                        }
                    )
                }
            }
        }
    }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF070A0F)) // Obsidian black background
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Use a single, shared GeckoView component to avoid rendering lag, memory overhead, and multiple compositor threads
                val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
                if (activeTab != null && !showHomeScreen) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GeckoView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                val runtime = viewModel.getGeckoRuntime(ctx)
                                if (!activeTab.session.isOpen) {
                                    activeTab.session.open(runtime)
                                }
                                setSession(activeTab.session)
                                activeTab.session.setActive(true)
                            }
                        },
                        update = { geckoView ->
                            val runtime = viewModel.getGeckoRuntime(geckoView.context)
                            if (!activeTab.session.isOpen) {
                                activeTab.session.open(runtime)
                            }
                            if (geckoView.session != activeTab.session) {
                                geckoView.setSession(activeTab.session)
                            }
                            activeTab.session.setActive(true)
                        }
                    )
                    
                    activeTab.loadError?.let { errorMsg ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF070A0F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF16222F)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1620)),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF4444),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Unable to Load Page",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorMsg,
                                        color = Color(0xFF8E9AA8),
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.reload() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088FF)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    DisposableEffect(activeTab) {
                        onDispose {
                            activeTab.session.setActive(false)
                        }
                    }
                }

                if (showHomeScreen) {
                    HomeScreenContent(
                        viewModel = viewModel,
                        onOpenDownloads = onOpenDownloads,
                        onOpenHistory = onOpenHistory,
                        onOpenLocker = onOpenLocker,
                        onOpenScanner = onOpenScanner,
                        onOpenQrTools = onOpenQrTools,
                        onOpenExtensions = { showExtensionsSheet = true },
                        onNavigateTo = { query ->
                            viewModel.loadUrl(query)
                        }
                    )
                }
            }

            // ─── Unified smart download button ─────────────────────────────────────
            // • Normal mode: always-visible FAB above the bottom bar when media detected
            // • Fullscreen: fades while playing, stays / reappears while paused or on tap
            val nonDrmMedia = detectedMedia.filter { !it.isDrmProtected }
            if (nonDrmMedia.isNotEmpty() && !showHomeScreen) {
                if (!viewModel.isFullscreen) {
                    // Regular mode — persistent button sitting above the 56 dp bottom bar
                    FloatingActionButton(
                        onClick = { showDownloadSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 72.dp) // 56 dp bar + 16 dp gap
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download Video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    // Fullscreen mode — overlay with auto-fade controls
                    // Transparent tap-catcher; restores controls on any tap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showFullscreenDownloadBtn = true }
                    ) {
                        // Top-left controls: Back + Exit Fullscreen
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .safeDrawingPadding()
                                .padding(start = 12.dp, top = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back to browser button
                                IconButton(
                                    onClick = { viewModel.goBack() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // Exit fullscreen button — tells GeckoView to exit fullscreen
                                IconButton(
                                    onClick = {
                                        val activeTab = viewModel.tabs.find { it.id == viewModel.activeTabId }
                                        activeTab?.session?.exitFullScreen()
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Bottom-right download button
                        AnimatedVisibility(
                            visible = showFullscreenDownloadBtn,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 32.dp)
                        ) {
                            FloatingActionButton(
                                onClick = { showDownloadSheet = true },
                                containerColor = Color.Black.copy(alpha = 0.78f),
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
            // ───────────────────────────────────────────────────────────────────────

            // Bottom options sheet for video downloading
            if (showDownloadSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Download Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(nonDrmMedia) { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (item.type == com.rebelroot.omni.media.MediaInterceptor.MediaType.AUDIO) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Quality: ${item.quality ?: "Auto/Source"}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Text(
                                                text = item.type.name,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    onPlayOnlineStream(item.url, viewModel.currentUrl)
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = "Video-${System.currentTimeMillis()}",
                                                            type = item.type,
                                                            saveToLocker = false,
                                                            referrerUrl = viewModel.currentUrl
                                                        )
                                                        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Save", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    showDownloadSheet = false
                                                    coroutineScope.launch {
                                                        viewModel.streamDownloadEngine.startDownload(
                                                            url = item.url,
                                                            suggestedName = "Video-${System.currentTimeMillis()}",
                                                            type = item.type,
                                                            saveToLocker = true,
                                                            referrerUrl = viewModel.currentUrl
                                                        )
                                                        Toast.makeText(context, "Downloading to Locker...", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Locker", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Offline translation dialog card overlay
            if (showTranslationDialog) {
                AlertDialog(
                    onDismissRequest = { showTranslationDialog = false; viewModel.translationManager.close() },
                    title = { Text("🌐 On-Device Offline Translator", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Download translation packages offline. 100% private.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            
                            OutlinedTextField(
                                value = translationSourceText,
                                onValueChange = { translationSourceText = it },
                                placeholder = { Text("Type text to translate...") },
                                modifier = Modifier.fillMaxWidth().height(90.dp),
                                shape = RoundedCornerShape(8.dp)
                            )

                            if (translationProgress) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    Text("Processing offline model...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (translationResultText.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = translationResultText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                translationProgress = true
                                // Setup translation dynamically from Spanish to English offline
                                viewModel.translationManager.setupLanguage(
                                    com.google.mlkit.nl.translate.TranslateLanguage.SPANISH,
                                    com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
                                ) {
                                    coroutineScope.launch {
                                        try {
                                            translationResultText = viewModel.translationManager.translateText(translationSourceText)
                                        } catch (e: Exception) {
                                            translationResultText = "Translation failed: ${e.message}"
                                        } finally {
                                            translationProgress = false
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Translate (ES ➔ EN)")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTranslationDialog = false; viewModel.translationManager.close() }) {
                            Text("Close")
                        }
                    }
                )
            }

            // 2. Premium Grid Tab Windows Switcher Tray Bottom Sheet
            if (showTabGroupsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTabGroupsSheet = false },
                    sheetState = rememberModalBottomSheetState(),
                    containerColor = Color(0xFF070A0F)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Open Tabs (${viewModel.tabs.size})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            TextButton(
                                onClick = {
                                    showTabGroupsSheet = false
                                    viewModel.createNewTab(context, "about:blank")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF0088FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Tab",
                                    color = Color(0xFF0088FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        val tabChunks = remember(viewModel.tabs) { viewModel.tabs.chunked(2) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            items(tabChunks) { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (tab in chunk) {
                                        val isActive = tab.id == viewModel.activeTabId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF16222F))
                                                .border(
                                                    BorderStroke(
                                                        if (isActive) 1.5.dp else 0.5.dp,
                                                        if (isActive) Color(0xFF0088FF) else Color(0xFF23374A)
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.selectTab(tab.id)
                                                    showTabGroupsSheet = false
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Header row of the tab window card
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Language,
                                                            contentDescription = null,
                                                            tint = if (isActive) Color(0xFF0088FF) else Color(0xFF8E9AA8),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = if (tab.title == "about:blank" || tab.title.isEmpty() || tab.url == "about:blank") "New Tab" else tab.title,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Box(
                                                         modifier = Modifier
                                                             .size(24.dp)
                                                             .clip(CircleShape)
                                                             .background(Color.White.copy(alpha = 0.1f))
                                                             .clickable {
                                                                 viewModel.closeTab(tab.id, context)
                                                             },
                                                         contentAlignment = Alignment.Center
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Rounded.Close,
                                                             contentDescription = "Close Tab",
                                                             tint = Color(0xFF8E9AA8),
                                                             modifier = Modifier.size(12.dp)
                                                         )
                                                     }
                                                }

                                                // Webpage Preview Placeholder Box (Chrome / Opera style)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(84.dp)
                                                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color(0xFF1E2D3F),
                                                                    Color(0xFF0F1B26)
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    // Show snapshot / reference image using high-res favicon
                                                    if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                                                        coil.compose.AsyncImage(
                                                            model = "https://www.google.com/s2/favicons?domain=${tab.url}&sz=128",
                                                            contentDescription = "Site Thumbnail",
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Explore,
                                                            contentDescription = null,
                                                            tint = Color.White.copy(alpha = 0.08f),
                                                            modifier = Modifier.size(40.dp)
                                                        )
                                                    }

                                                    // Tiny URL overlay at the bottom of the card preview
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(6.dp),
                                                        contentAlignment = Alignment.BottomStart
                                                    ) {
                                                        Text(
                                                            text = if (tab.url == "about:blank") "about:blank" else tab.url,
                                                            color = Color(0xFF8E9AA8).copy(alpha = 0.8f),
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (chunk.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Web Extensions Manager Bottom Sheet
            if (showConsoleSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showConsoleSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF0D1620)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ">_ Developer Console",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF0088FF)
                            )
                            TextButton(onClick = { viewModel.consoleLogs.clear() }) {
                                Text("Clear", color = Color(0xFFFF5555))
                            }
                        }

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .background(Color(0xFF05080C), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF23374A), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.consoleLogs.toList()) { log ->
                                val color = when (log.level) {
                                    "ERROR" -> Color(0xFFFF5555)
                                    "WARN" -> Color(0xFFFFB86C)
                                    "INFO" -> Color(0xFF8BE9FD)
                                    else -> Color(0xFFF8F8F2)
                                }
                                val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                val timeStr = formatter.format(java.util.Date(log.timestamp))
                                
                                Text(
                                    text = "[$timeStr] ${log.level}: ${log.message}",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = color,
                                    lineHeight = 14.sp
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }

            // 4. Web Extensions Manager Bottom Sheet
            if (showExtensionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showExtensionsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = Color(0xFF0D1620)
                ) {
                    // Sync user extensions safely when sheet opens
                    LaunchedEffect(Unit) {
                        try {
                            viewModel.syncUserExtensions()
                        } catch (_: Exception) { /* ignore sync errors */ }
                    }

                    // Take a stable snapshot to avoid ConcurrentModificationException
                    val userExts = remember(viewModel.userExtensions.toList()) {
                        viewModel.userExtensions.toList()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🧩 Web Extensions Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0088FF)
                        )
                        Text(
                            text = "Manage GeckoView WebExtensions. Enhance your privacy and control.",
                            fontSize = 11.sp,
                            color = Color(0xFF8E9AA8)
                        )

                        Button(
                            onClick = {
                                showExtensionsSheet = false
                                viewModel.createNewTab(context, "https://addons.mozilla.org/en-US/android/")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = "Extension Store", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Official Firefox Add-ons Store", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f))

                        // Extension Item 1: uBlock Origin
                        ExtensionItemCard(
                            icon = Icons.Rounded.Shield,
                            name = "uBlock Origin",
                            author = "Raymond Hill (gorhill)",
                            description = "An efficient wide-spectrum content blocker. Easy on CPU and memory.",
                            checked = viewModel.isAdblockerEnabled,
                            onCheckedChange = { viewModel.toggleAdblock(context) }
                        )

                        // Extension Item 2: Universal Text Copy
                        ExtensionItemCard(
                            icon = Icons.Rounded.FileCopy,
                            name = "Universal Text Copy",
                            author = "Omni Browser Team",
                            description = "Bypass website restrictions to force-enable text selection and copying.",
                            checked = viewModel.isUniversalCopyEnabled,
                            onCheckedChange = { viewModel.toggleUniversalCopy(context) }
                        )

                        // Extension Item 3: Media Grabber
                        ExtensionItemCard(
                            icon = Icons.Rounded.Download,
                            name = "Aggressive Media Grabber",
                            author = "Omni Browser Team",
                            description = "Sniff and capture offline dynamic HLS/DASH dynamic segments and streams.",
                            checked = viewModel.isMediaGrabberEnabled,
                            onCheckedChange = { viewModel.toggleMediaGrabber(context) }
                        )

                        // User-installed Extensions Title and Cards
                        if (userExts.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFF23374A).copy(alpha = 0.5f))
                            Text(
                                text = "⬇️ Installed Web Extensions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0088FF)
                            )
                            
                            for (ext in userExts) {
                                val isEnabled = ext.metaData.enabled
                                val optionsUrl = try { ext.metaData?.optionsPage } catch (e: Exception) { null }
                                UserExtensionItemCard(
                                    extension = ext,
                                    checked = isEnabled,
                                    onCheckedChange = { viewModel.toggleUserExtension(ext, context) },
                                    onUninstall = { viewModel.uninstallUserExtension(ext, context) },
                                    onOptionsClick = if (!optionsUrl.isNullOrBlank()) {
                                        {
                                            showExtensionsSheet = false
                                            viewModel.loadUrl(optionsUrl)
                                        }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            
            // Site permission prompt overlay
            viewModel.activePermissionPrompt?.let { prompt ->
                PermissionPromptDialog(prompt = prompt)
            }

            // Site WebRTC media permission prompt overlay
            viewModel.activeMediaPermissionPrompt?.let { prompt ->
                MediaPermissionPromptDialog(prompt = prompt)
            }
        }
    }
}

@Composable
fun ExtensionItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    author: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16222F),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    if (!enabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "CORE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "by $author",
                    fontSize = 10.sp,
                    color = Color(0xFFAAB8C2)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 14.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
fun UserExtensionItemCard(
    extension: org.mozilla.geckoview.WebExtension,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onOptionsClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16222F),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                val extId = try { extension.id ?: "unknown-extension" } catch (e: Exception) { "unknown-extension" }
                val displayName = remember(extId) {
                    val name = try { extension.metaData?.name } catch (e: Exception) { null }
                    if (!name.isNullOrBlank()) {
                        name
                    } else {
                        extId.substringBefore("@").replace("-", " ")
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                }
                Text(text = displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text(
                    text = "ID: $extId",
                    fontSize = 10.sp,
                    color = Color(0xFFAAB8C2)
                )
            }

            if (onOptionsClick != null) {
                IconButton(
                    onClick = onOptionsClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF0088FF))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Options"
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )

            IconButton(
                onClick = onUninstall,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Uninstall"
                )
            }
        }
    }
}

@Composable
fun PermissionPromptDialog(prompt: com.rebelroot.omni.browser.ContentPermissionPrompt) {
    val icon = when (prompt.permissionType) {
        1 -> Icons.Rounded.LocationOn // PERMISSION_GEOLOCATION
        3 -> Icons.Rounded.CameraAlt  // PERMISSION_CAMERA
        4 -> Icons.Rounded.Mic        // PERMISSION_MICROPHONE
        5 -> Icons.Rounded.VpnKey     // PERMISSION_MEDIA_KEY_SYSTEM
        8 -> Icons.Rounded.Storage    // PERMISSION_STORAGE_ACCESS
        else -> Icons.Rounded.Info
    }
    
    val title = when (prompt.permissionType) {
        1 -> "Location Access"
        3 -> "Camera Access"
        4 -> "Microphone Access"
        5 -> "DRM Media Access"
        8 -> "Storage Access"
        else -> "Permission Request"
    }

    val description = when (prompt.permissionType) {
        1 -> "wants to access your physical location to provide localized services and content."
        3 -> "wants to use your camera for video streaming or recording."
        4 -> "wants to use your microphone for voice recording or calling."
        5 -> "wants to verify your device DRM keys for secure high-definition playback."
        8 -> "wants to read/write storage for managing site offline cache or local files."
        else -> "is requesting access to permissions."
    }

    AlertDialog(
        onDismissRequest = { prompt.onDeny() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0088FF).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF0088FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = prompt.siteUri,
                    color = Color(0xFF0088FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = Color(0xFF8E9AA8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { prompt.onAllow() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0088FF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Allow", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { prompt.onDeny() },
                border = BorderStroke(0.5.dp, Color(0xFF16222F)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF8E9AA8)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Deny", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFF0D1620),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(BorderStroke(0.5.dp, Color(0xFF16222F)), RoundedCornerShape(16.dp))
    )
}

@Composable
fun MediaPermissionPromptDialog(prompt: com.rebelroot.omni.browser.MediaPermissionPrompt) {
    val title = when {
        prompt.hasVideo && prompt.hasAudio -> "Camera & Microphone"
        prompt.hasVideo -> "Camera Access"
        else -> "Microphone Access"
    }

    val description = when {
        prompt.hasVideo && prompt.hasAudio -> "wants to use your camera and microphone for video capture or calling."
        prompt.hasVideo -> "wants to use your camera for video capture or streaming."
        else -> "wants to use your microphone for voice recording or calling."
    }

    val icon = when {
        prompt.hasVideo && prompt.hasAudio -> Icons.Rounded.Videocam
        prompt.hasVideo -> Icons.Rounded.CameraAlt
        else -> Icons.Rounded.Mic
    }

    AlertDialog(
        onDismissRequest = { prompt.onDeny() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0088FF).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF0088FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = prompt.siteUri,
                    color = Color(0xFF0088FF),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = Color(0xFF8E9AA8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { prompt.onAllow(null, null) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0088FF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Allow", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { prompt.onDeny() },
                border = BorderStroke(0.5.dp, Color(0xFF16222F)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF8E9AA8)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Deny", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFF0D1620),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(BorderStroke(0.5.dp, Color(0xFF16222F)), RoundedCornerShape(16.dp))
    )
}

@Composable
fun MenuGridCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xFF8E9AA8),
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
