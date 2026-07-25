/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.rebelroot.omni.browser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.io.File
import java.io.FileOutputStream

// ─── Data model ────────────────────────────────────────────────────────────
data class OnlineWallpaper(
    val id: String,
    val thumbUrl: String,   // 400px thumbnail for grid
    val fullUrl: String,    // 1600px full res
    val description: String,
    val photographer: String,
    val color: String = "#1C1C1E"
)

// ─── Category definitions ───────────────────────────────────────────────────
val WALLPAPER_CATEGORIES = listOf(
    "Featured"   to "featured",
    "Nature"     to "nature",
    "Space"      to "space",
    "Abstract"   to "abstract",
    "City"       to "city",
    "Ocean"      to "ocean",
    "Minimal"    to "minimal",
    "Dark"       to "dark",
    "Neon"       to "neon",
    "Mountains"  to "mountain",
    "Flowers"    to "flowers",
    "Technology" to "technology"
)

// ─── Picsum Photos URL builder (free, no API key, always works) ───────────────
// https://picsum.photos — uses seeded random for consistency
private fun picsumThumb(seed: String) = "https://picsum.photos/seed/$seed/400/600"
private fun picsumFull(seed: String)  = "https://picsum.photos/seed/$seed/1600/2560"

// Generate a large deterministic collection per category
private fun generateCategoryWallpapers(categoryKey: String, count: Int = 48): List<OnlineWallpaper> {
    return (1..count).map { i ->
        val seed = "${categoryKey}_$i"
        OnlineWallpaper(
            id          = seed,
            thumbUrl    = picsumThumb(seed),
            fullUrl     = picsumFull(seed),
            description = categoryKey.replaceFirstChar { it.uppercase() },
            photographer = "Picsum Photos"
        )
    }
}

val PRESET_WALLPAPERS = listOf(
    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1604871000636-074fa5117945?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1563089145-599997674d42?q=80&w=600&auto=format&fit=crop",
    "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=600&auto=format&fit=crop"
)

// ─── Download helper ─────────────────────────────────────────────────────────
suspend fun downloadWallpaperToFile(context: Context, url: String): String? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(url).openConnection()
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()
        val input = conn.getInputStream()
        val file = File(context.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
        val output = FileOutputStream(file)
        input.copyTo(output)
        output.close()
        input.close()
        android.net.Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    viewModel: BrowserViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var editingWallpaperUri by remember { mutableStateOf<String?>(null) }
    var showOnlineGallery by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
            } catch (e: Exception) { }
            editingWallpaperUri = it.toString()
        }
    }

    val isDark = viewModel.isDarkThemeEnabled
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedWallpaper = viewModel.browserWallpaperUri

    when {
        editingWallpaperUri != null -> {
            WallpaperEditorView(
                uri = editingWallpaperUri!!,
                viewModel = viewModel,
                onDismiss = { editingWallpaperUri = null },
                onApply = { cropUri, scale, offsetX, offsetY, dim, blur ->
                    viewModel.saveBrowserWallpaperUri(context, cropUri)
                    viewModel.saveWallpaperCrop(context, scale, offsetX, offsetY)
                    viewModel.saveWallpaperDim(context, dim)
                    viewModel.saveWallpaperBlur(context, blur)
                    editingWallpaperUri = null
                }
            )
        }
        showOnlineGallery -> {
            OnlineWallpaperGallery(
                viewModel = viewModel,
                onBack = { showOnlineGallery = false },
                onEditWallpaper = { uri -> editingWallpaperUri = uri }
            )
        }
        else -> {
            WallpaperHome(
                viewModel = viewModel,
                onBack = onNavigateBack,
                onOpenGallery = { showOnlineGallery = true },
                onPickPhoto = { launcher.launch("image/*") },
                onEditWallpaper = { editingWallpaperUri = it },
                bgColor = bgColor, cardColor = cardColor, cardBorder = cardBorder,
                textPrimary = textPrimary, textSecondary = textSecondary,
                accent = accent, isDark = isDark
            )
        }
    }
}

// ─── Home / Picker Screen ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperHome(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onPickPhoto: () -> Unit,
    onEditWallpaper: (String) -> Unit,
    bgColor: Color, cardColor: Color, cardBorder: Color,
    textPrimary: Color, textSecondary: Color,
    accent: Color, isDark: Boolean
) {
    val context = LocalContext.current
    val selectedWallpaper = viewModel.browserWallpaperUri

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallpapers", fontWeight = FontWeight.Bold, color = textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(BorderStroke(0.5.dp, cardBorder.copy(alpha = 0.2f)))
            )
        },
        containerColor = bgColor
    ) { pv ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(pv)
        ) {

            // ── Action row ──────────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // My photos
                    OutlinedButton(
                        onClick = onPickPhoto,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("My Photos", fontSize = 13.sp)
                    }
                    // Online gallery — main CTA
                    Button(
                        onClick = onOpenGallery,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Online", fontSize = 13.sp)
                    }
                }
            }

            // ── Daily rotation toggle ───────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = cardColor,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Change wallpaper daily", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Auto-rotate from your collection", color = textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = viewModel.changeWallpaperDaily,
                            onCheckedChange = { viewModel.saveChangeWallpaperDaily(context, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = accent)
                        )
                    }
                }
            }

            // ── Customization sliders (only if wallpaper active) ─────────────
            val isActive = selectedWallpaper != null && selectedWallpaper.isNotEmpty() && selectedWallpaper != "null"
            if (isActive) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = cardColor, shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Tune, null, tint = accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Wallpaper Customization", color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            val dimVal = if (viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else (if (isDark) 0.5f else 0.65f)
                            SliderRow("Blur", "${viewModel.wallpaperBlur.toInt()} dp", viewModel.wallpaperBlur, 0f..25f, accent, textPrimary, textSecondary) { viewModel.saveWallpaperBlur(context, it) }
                            HorizontalDivider(color = cardBorder.copy(alpha = 0.4f))
                            SliderRow("Dim", "${(dimVal * 100).toInt()}%", dimVal, 0f..0.9f, accent, textPrimary, textSecondary) { viewModel.saveWallpaperDim(context, it) }
                        }
                    }
                }
            }

            // ── Section header ──────────────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Your Library", color = textPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp))
            }

            // ── No wallpaper tile ───────────────────────────────────────────
            item {
                val sel = selectedWallpaper == null || selectedWallpaper == "null" || selectedWallpaper.isEmpty()
                WallpaperTile(
                    imageUrl = null, isSelected = sel, accent = accent, cardColor = cardColor, cardBorder = cardBorder,
                    onClick = { viewModel.saveBrowserWallpaperUri(context, null) }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.HideImage, null, tint = textSecondary, modifier = Modifier.size(28.dp))
                            Text("None", color = textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Active custom wallpaper ─────────────────────────────────────
            val isCustomActive = isActive && !PRESET_WALLPAPERS.contains(selectedWallpaper)
            if (isCustomActive) {
                item {
                    WallpaperTile(imageUrl = selectedWallpaper, isSelected = true, accent = accent,
                        cardColor = cardColor, cardBorder = cardBorder, onClick = { onEditWallpaper(selectedWallpaper!!) })
                }
            }

            // ── Preset tiles ────────────────────────────────────────────────
            items(PRESET_WALLPAPERS) { uri ->
                val sel = selectedWallpaper == uri
                WallpaperTile(
                    imageUrl = uri, isSelected = sel, accent = accent,
                    cardColor = cardColor, cardBorder = cardBorder,
                    onClick = { viewModel.saveBrowserWallpaperUri(context, uri) }
                )
            }
        }
    }
}

// ─── Online Gallery Screen ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineWallpaperGallery(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onEditWallpaper: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = viewModel.isDarkThemeEnabled

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var activeSearch by remember { mutableStateOf("") }

    // Wallpaper list — derived from category or search
    val wallpapers: List<OnlineWallpaper> = remember(selectedCategoryIndex, activeSearch) {
        if (activeSearch.isNotBlank()) {
            generateCategoryWallpapers(activeSearch, 60)
        } else {
            generateCategoryWallpapers(WALLPAPER_CATEGORIES[selectedCategoryIndex].second, 60)
        }
    }

    // Download state per tile
    var downloadingId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
            ) {
                TopAppBar(
                    title = { Text("Wallpaper Gallery", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
                )

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search wallpapers…", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; activeSearch = "" }) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        activeSearch = searchQuery
                        focusManager.clearFocus()
                    }),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Category tabs
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(WALLPAPER_CATEGORIES) { index, (label, _) ->
                        val isSelected = selectedCategoryIndex == index && activeSearch.isEmpty()
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.surface,
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedCategoryIndex = index
                                activeSearch = ""
                                searchQuery = ""
                            }
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        },
        containerColor = bgColor
    ) { pv ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(pv)
        ) {
            items(wallpapers, key = { it.id }) { wp ->
                OnlineWallpaperTile(
                    wallpaper = wp,
                    isSelected = viewModel.browserWallpaperUri == wp.fullUrl,
                    isDownloading = downloadingId == wp.id,
                    accent = accent,
                    onTap = {
                        // Apply directly via URL (no download needed for online URLs)
                        viewModel.saveBrowserWallpaperUri(context, wp.fullUrl)
                        Toast.makeText(context, "Wallpaper applied!", Toast.LENGTH_SHORT).show()
                    },
                    onLongPress = {
                        // Download and edit
                        scope.launch {
                            downloadingId = wp.id
                            val localUri = downloadWallpaperToFile(context, wp.fullUrl)
                            downloadingId = null
                            if (localUri != null) {
                                onEditWallpaper(localUri)
                            } else {
                                Toast.makeText(context, "Download failed. Check connection.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

// ─── Online Wallpaper Tile ───────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnlineWallpaperTile(
    wallpaper: OnlineWallpaper,
    isSelected: Boolean,
    isDownloading: Boolean,
    accent: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")

    Box(
        modifier = Modifier
            .aspectRatio(0.65f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .border(
                if (isSelected) 2.5.dp else 0.dp,
                if (isSelected) accent else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(wallpaper.thumbUrl)
                .crossfade(400)
                .build(),
            contentDescription = wallpaper.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF1C1C1E), Color(0xFF2C2C2E)))
                    )
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
            }
        )

        // Gradient overlay + info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(wallpaper.description, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Tap • Long-press to edit", color = Color.White.copy(0.55f), fontSize = 9.sp)
            }
        }

        // Selected badge
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        // Downloading overlay
        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text("Downloading…", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─── Library Tile (preset/custom) ───────────────────────────────────────────
@Composable
private fun WallpaperTile(
    imageUrl: String?,
    isSelected: Boolean,
    accent: Color,
    cardColor: Color,
    cardBorder: Color,
    onClick: () -> Unit,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(
                if (isSelected) 2.5.dp else 0.5.dp,
                if (isSelected) accent else cardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            content?.invoke(this)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─── Slider helper ───────────────────────────────────────────────────────────
@Composable
private fun SliderRow(
    label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color, textPrimary: Color, textSecondary: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textPrimary, fontSize = 14.sp)
            Text(valueLabel, color = textSecondary, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }
}

// ─── Wallpaper Editor (unchanged) ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperEditorView(
    uri: String,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onApply: (String, Float, Float, Float, Float, Float) -> Unit
) {
    val isDarkMode = viewModel.isDarkThemeEnabled
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val cardBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    val isEditingCurrent = uri == viewModel.browserWallpaperUri
    var tempScale   by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperScale else 1.0f) }
    var tempOffsetX by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperOffsetX else 0f) }
    var tempOffsetY by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperOffsetY else 0f) }
    var tempDim     by remember { mutableStateOf(if (isEditingCurrent && viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else 0.4f) }
    var tempBlur    by remember { mutableStateOf(if (isEditingCurrent) viewModel.wallpaperBlur else 0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Wallpaper", fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close", tint = textPrimaryColor)
                    }
                },
                actions = {
                    TextButton(onClick = { onApply(uri, tempScale, tempOffsetX, tempOffsetY, tempDim, tempBlur) }) {
                        Text("Apply", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.border(BorderStroke(0.5.dp, cardBorderColor.copy(alpha = 0.2f)))
            )
        },
        containerColor = bgColor
    ) { pv ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Preview phone
            Surface(
                modifier = Modifier.width(260.dp).aspectRatio(9f / 16f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(4.dp, cardBorderColor),
                shadowElevation = 12.dp,
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = tempScale; scaleY = tempScale; translationX = tempOffsetX; translationY = tempOffsetY }
                            .then(if (tempBlur > 0f) Modifier.blur(tempBlur.dp).graphicsLayer() else Modifier)
                    ) {
                        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tempDim)))
                    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures { change, drag -> change.consume(); tempOffsetX += drag.x; tempOffsetY += drag.y }
                    })
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .background(Color.Black.copy(0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Drag to reposition", color = Color.White.copy(0.9f), fontSize = 9.sp)
                    }
                }
            }

            // Controls
            Surface(color = cardColor, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SliderRow("Zoom / Scale", String.format("%.1fx", tempScale), tempScale, 1f..3f, accentColor, textPrimaryColor, textSecondaryColor) { tempScale = it }
                    SliderRow("Blur amount", "${tempBlur.toInt()} dp", tempBlur, 0f..25f, accentColor, textPrimaryColor, textSecondaryColor) { tempBlur = it }
                    SliderRow("Dim opacity", "${(tempDim * 100).toInt()}%", tempDim, 0f..0.9f, accentColor, textPrimaryColor, textSecondaryColor) { tempDim = it }
                    Button(
                        onClick = { tempScale = 1f; tempOffsetX = 0f; tempOffsetY = 0f },
                        colors = ButtonDefaults.buttonColors(containerColor = cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, "Reset", tint = textPrimaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Position", color = textPrimaryColor)
                    }
                }
            }
        }
    }
}
