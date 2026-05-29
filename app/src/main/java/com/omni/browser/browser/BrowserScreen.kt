package com.omni.browser.browser

import android.app.Activity
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import com.omni.browser.media.MediaInterceptor
import com.omni.browser.privacy.FireButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenLocker: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenQrTools: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    
    var showMenu by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf(viewModel.currentUrl) }
    
    // Video detection states
    val detectedMedia by viewModel.mediaInterceptor.detectedMedia.collectAsState()
    var showDownloadSheet by remember { mutableStateOf(false) }
    var selectedMediaItem by remember { mutableStateOf<MediaInterceptor.DetectedMedia?>(null) }

    // Offline Translation states
    var showTranslationDialog by remember { mutableStateOf(false) }
    var translationSourceText by remember { mutableStateOf("") }
    var translationResultText by remember { mutableStateOf("") }
    var translationProgress by remember { mutableStateOf(false) }

    // Smart Tab Grouping states
    var showTabGroupsSheet by remember { mutableStateOf(false) }
    val openTabs = remember {
        listOf(
            com.omni.browser.browser.tabs.BrowserTab("1", "YouTube - Home", "https://youtube.com"),
            com.omni.browser.browser.tabs.BrowserTab("2", "StackOverflow - GeckoView", "https://stackoverflow.com"),
            com.omni.browser.browser.tabs.BrowserTab("3", "Amazon - Shopping Cart", "https://amazon.com"),
            com.omni.browser.browser.tabs.BrowserTab("4", "Kotlin Docs", "https://kotlinlang.org"),
            com.omni.browser.browser.tabs.BrowserTab("5", "Google Search", "https://google.com")
        )
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

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !viewModel.isFullscreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeChild(state = hazeState)
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
                                // Logo with biometric Locker long-press
                                Box(
                                    modifier = Modifier
                                        .size(36dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = { showTabGroupsSheet = true }, // Opens Smart Tab Group switch tray
                                            onLongClick = { onOpenLocker() }
                                        )
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Menu,
                                        contentDescription = "Omni Logo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.goBack() },
                                    enabled = viewModel.canGoBack,
                                    modifier = Modifier.size(36dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (viewModel.canGoBack) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.goForward() },
                                    enabled = viewModel.canGoForward,
                                    modifier = Modifier.size(36dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Forward",
                                        tint = if (viewModel.canGoForward) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                    )
                                }

                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42dp)
                                        .padding(horizontal = 4.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            viewModel.loadUrl(inputUrl)
                                            keyboardController?.hide()
                                        }
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Search icon",
                                            modifier = Modifier.size(16dp),
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    },
                                    trailingIcon = {
                                        if (inputUrl.isNotEmpty()) {
                                            IconButton(
                                                onClick = { inputUrl = "" },
                                                modifier = Modifier.size(16dp)
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
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                )

                                IconButton(
                                    onClick = { viewModel.reload() },
                                    modifier = Modifier.size(36dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Reload",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(36dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Browser Tools",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState),
                factory = { ctx ->
                    GeckoView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val runtime = viewModel.getGeckoRuntime(ctx)
                        setSession(viewModel.geckoSession)
                        viewModel.geckoSession.open(runtime)
                        viewModel.loadUrl(viewModel.currentUrl)
                    }
                }
            )

            // Dynamic bottom-right download FAB button when media streams are detected (excluding DRM)
            val nonDrmMedia = detectedMedia.filter { !it.isDrmProtected }
            if (nonDrmMedia.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        selectedMediaItem = nonDrmMedia.first()
                        showDownloadSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Download Video",
                        tint = Color.White,
                        modifier = Modifier.size(32dp)
                    )
                }
            }

            // Options drop-down menu
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 4.dp)
            ) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("📄 Document Scanner") },
                        onClick = { showMenu = false; onOpenScanner() }
                    )
                    DropdownMenuItem(
                        text = { Text("📱 QR & Barcode Tools") },
                        onClick = { showMenu = false; onOpenQrTools() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔒 Private Locker Room") },
                        onClick = { showMenu = false; onOpenLocker() }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = { Text("⬇️ Downloads Dashboard") },
                        onClick = { showMenu = false; onOpenDownloads() }
                    )
                    DropdownMenuItem(
                        text = { Text("📖 Reader Mode") },
                        onClick = {
                            showMenu = false
                            try {
                                viewModel.toggleReaderMode()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Reader Mode not supported on this page", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = { Text("🌐 Offline Translation") },
                        onClick = { showMenu = false; showTranslationDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DropdownMenuItem(
                        text = { Text("🔥 Burn All Data") },
                        onClick = {
                            showMenu = false
                            coroutineScope.launch {
                                val runtime = viewModel.getGeckoRuntime(context)
                                FireButton(runtime, context).burn()
                                Toast.makeText(context, "🔥 Browsing data burned", Toast.LENGTH_SHORT).show()
                                viewModel.loadUrl("https://google.com")
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("⚙️ Settings Panel") },
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                }
            }

            // Bottom options sheet for video downloading
            if (showDownloadSheet && selectedMediaItem != null) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Download Detected Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = selectedMediaItem!!.url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 2
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    showDownloadSheet = false
                                    coroutineScope.launch {
                                        viewModel.streamDownloadEngine.startDownload(
                                            url = selectedMediaItem!!.url,
                                            suggestedName = "Video-${System.currentTimeMillis()}",
                                            type = selectedMediaItem!!.type,
                                            saveToLocker = false
                                        )
                                        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Download")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    showDownloadSheet = false
                                    coroutineScope.launch {
                                        viewModel.streamDownloadEngine.startDownload(
                                            url = selectedMediaItem!!.url,
                                            suggestedName = "SecureVideo-${System.currentTimeMillis()}",
                                            type = selectedMediaItem!!.type,
                                            saveToLocker = true
                                        )
                                        Toast.makeText(context, "Secure Locker download started...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = "Secure")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download Privately", fontSize = 13.sp)
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
                                    CircularProgressIndicator(modifier = Modifier.size(16dp))
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

            // 2. Smart Tab Grouping Switcher Tray Bottom Sheet
            if (showTabGroupsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTabGroupsSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📂 Smart Tab Groups", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Tabs are categorized automatically by domains using AI.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(6.dp))

                        val categorized = remember { com.omni.browser.browser.tabs.SmartTabManager().categorize(openTabs) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            items(categorized) { group ->
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(group.icon, fontSize = 16.sp)
                                        Text(group.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(${group.tabs.size})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    group.tabs.forEach { tab ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .clickable {
                                                    showTabGroupsSheet = false
                                                    viewModel.loadUrl(tab.url)
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.Search, contentDescription = "Tab Icon", modifier = Modifier.size(16dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(tab.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(tab.url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
