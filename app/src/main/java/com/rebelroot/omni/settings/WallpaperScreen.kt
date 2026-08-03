/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.content.Context
import android.os.Build
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
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
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
    "live_wallpapers",
    "featured",
    "nature",
    "space",
    "abstract",
    "city",
    "ocean",
    "minimal",
    "dark",
    "neon",
    "mountain",
    "flowers",
    "technology"
)

@Composable
private fun wallpaperCategoryLabel(key: String): String = when (key) {
    "live_wallpapers" -> stringResource(R.string.wallpaper_cat_live)
    "featured"        -> stringResource(R.string.wallpaper_cat_featured)
    "nature"          -> stringResource(R.string.wallpaper_cat_nature)
    "space"           -> stringResource(R.string.wallpaper_cat_space)
    "abstract"        -> stringResource(R.string.wallpaper_cat_abstract)
    "city"            -> stringResource(R.string.wallpaper_cat_city)
    "ocean"           -> stringResource(R.string.wallpaper_cat_ocean)
    "minimal"         -> stringResource(R.string.wallpaper_cat_minimal)
    "dark"            -> stringResource(R.string.wallpaper_cat_dark)
    "neon"            -> stringResource(R.string.wallpaper_cat_neon)
    "mountain"        -> stringResource(R.string.wallpaper_cat_mountains)
    "flowers"         -> stringResource(R.string.wallpaper_cat_flowers)
    "technology"      -> stringResource(R.string.wallpaper_cat_technology)
    else              -> key.replaceFirstChar { it.uppercaseChar() }
}

// ─── Picsum Photos URL builder (free, no API key, always works) ───────────────
// https://picsum.photos — uses seeded random for consistency
private fun picsumThumb(seed: String) = "https://picsum.photos/seed/$seed/400/600"
private fun picsumFull(seed: String)  = "https://picsum.photos/seed/$seed/1600/2560"

// Generate a large deterministic collection per category
private fun generateCategoryWallpapers(categoryKey: String, count: Int = 48): List<OnlineWallpaper> {
    if (categoryKey == "live_wallpapers") {
        val livePresets = listOf(
            Pair("Calm Ocean Waves", "https://videos.pexels.com/video-files/5853147/5853147-hd_2048_1080_30fps.mp4"),
            Pair("Sunset Horizon", "https://videos.pexels.com/video-files/11335978/11335978-hd_1920_1080_30fps.mp4"),
            Pair("Sunlight Through Trees", "https://videos.pexels.com/video-files/11265968/11265968-hd_1920_1080_25fps.mp4"),
            Pair("City Skyscrapers", "https://videos.pexels.com/video-files/12685044/12685044-hd_1920_1080_30fps.mp4"),
            Pair("Rainy Night City", "https://videos.pexels.com/video-files/855432/855432-hd_1840_1034_25fps.mp4"),
            Pair("Abstract Colors", "https://videos.pexels.com/video-files/10881637/10881637-hd_1920_1080_25fps.mp4"),
            Pair("Plants by River", "https://videos.pexels.com/video-files/1208094/1208094-hd_1920_1080_30fps.mp4"),
            Pair("Walking in Woods", "https://videos.pexels.com/video-files/8424070/8424070-hd_1920_1080_30fps.mp4"),
            Pair("Pinterest City Clip", "https://v1.pinimg.com/videos/iht/hls/d0/b6/93/d0b69328b1f41ad0271fe4374baa688b.m3u8"),
            Pair("Matrix Digital Code", "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExdW9uaXZ4OWYwNGd1bmM1c3Q4ZGFzeXJ4NnM2azE5enptcW5vdDVzdiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/L1R1tvI9svkIWwpVYr/giphy.gif"),
            Pair("Lo-Fi Coffee Shop", "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpucjNlOWRxeWFvZmVudDFndDFjcHZtZ3d1NHEycDdmcDduYmsyeCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/d480lR5b5h8k9X0M/giphy.gif"),
            Pair("Blue Sea Waves", "https://videos.pexels.com/video-files/5668625/5668625-hd_2048_1080_30fps.mp4")
        )
        return livePresets.mapIndexed { idx, (title, url) ->
            val isVid = url.endsWith(".mp4") || url.endsWith(".m3u8")
            val source = when {
                url.contains("pexels.com") -> "Pexels Video"
                url.contains("giphy.com") -> "Giphy Animation"
                url.contains("pinimg.com") -> "Pinterest Video"
                else -> "Stock Video"
            }
            val thumb = when (idx) {
                0 -> "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400"
                1 -> "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=400"
                2 -> "https://images.unsplash.com/photo-1448375240586-882707db888b?w=400"
                3 -> "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=400"
                4 -> "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=400"
                5 -> "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=400"
                6 -> "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=400"
                7 -> "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=400"
                8 -> "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400"
                9 -> "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400"
                10 -> "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400"
                else -> "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=400"
            }
            OnlineWallpaper(
                id = "live_preset_$idx",
                thumbUrl = thumb,
                fullUrl = url,
                description = title,
                photographer = source
            )
        }
    }
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
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
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
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { inputUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val mime = context.contentResolver.getType(inputUri) ?: ""
                    val ext = when {
                        mime.contains("video/mp4") -> "mp4"
                        mime.contains("video/webm") -> "webm"
                        mime.contains("image/gif") -> "gif"
                        else -> "jpg"
                    }
                    val localFile = File(context.filesDir, "custom_wallpaper_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        FileOutputStream(localFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val localUri = android.net.Uri.fromFile(localFile).toString()
                    withContext(Dispatchers.Main) {
                        viewModel.saveBrowserWallpaperUri(context, localUri)
                        editingWallpaperUri = localUri
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        editingWallpaperUri = inputUri.toString()
                    }
                }
            }
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
                    viewModel.saveAllWallpaperSettings(context, cropUri, scale, offsetX, offsetY, dim, blur)
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
                onPickPhoto = { launcher.launch("*/*") },
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
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlText by remember { mutableStateOf("") }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text(stringResource(id = R.string.wallpaper_custom_url_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = R.string.wallpaper_custom_url_desc), fontSize = 13.sp, color = textSecondary)
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        placeholder = { Text(stringResource(id = R.string.wallpaper_custom_url_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = customUrlText.trim()
                        if (clean.isNotBlank()) {
                            // Download first instead of saving raw URL (Fix #9)
                            viewModel.downloadAndSetWallpaper(context, clean) { success ->
                                if (!success) {
                                    // Fallback: save remote URL if download fails
                                    viewModel.saveBrowserWallpaperUri(context, clean)
                                }
                            }
                            showCustomUrlDialog = false
                            customUrlText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(stringResource(id = R.string.wallpaper_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text(stringResource(id = R.string.cancel_text))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.wallpapers_title), fontWeight = FontWeight.Bold, color = textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(id = R.string.back_desc), tint = textPrimary)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // My Media (GIF/Video/Photo)
                        OutlinedButton(
                            onClick = onPickPhoto,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(id = R.string.wallpaper_local_file), fontSize = 13.sp)
                        }
                        // Custom Direct URL
                        OutlinedButton(
                            onClick = { showCustomUrlDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(id = R.string.wallpaper_paste_url), fontSize = 13.sp)
                        }
                    }
                    // Online gallery — main CTA
                    Button(
                        onClick = onOpenGallery,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.wallpaper_online_gallery), fontSize = 13.sp)
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
                            Text(stringResource(id = R.string.wallpaper_daily_title), color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(id = R.string.wallpaper_daily_desc), color = textSecondary, fontSize = 12.sp)
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
                                Text(stringResource(id = R.string.wallpaper_customization), color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            val dimVal = if (viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else 0.20f
                            SliderRow(stringResource(id = R.string.wallpaper_blur), "${viewModel.wallpaperBlur.toInt()} dp", viewModel.wallpaperBlur, 0f..25f, accent, textPrimary, textSecondary) { viewModel.saveWallpaperBlur(context, it) }
                            HorizontalDivider(color = cardBorder.copy(alpha = 0.4f))
                            SliderRow(stringResource(id = R.string.wallpaper_dim), "${(dimVal * 100).toInt()}%", dimVal, 0f..0.9f, accent, textPrimary, textSecondary) { viewModel.saveWallpaperDim(context, it) }
                        }
                    }
                }
            }

            // ── Live Animated Wallpapers Section ─────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Videocam, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(id = R.string.wallpaper_live_section),
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp)
                    )
                }
            }

            items(com.rebelroot.omni.settings.LIVE_ANIMATED_WALLPAPERS) { preset ->
                val isDownloadingThis = viewModel.isWallpaperDownloading && viewModel.downloadingWallpaperUrl == preset.mediaUrl
                // Match by original URL, preset id, or MD5 hash in downloaded filename
                val presetHash = remember(preset.mediaUrl) {
                    java.security.MessageDigest.getInstance("MD5")
                        .digest(preset.mediaUrl.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }
                val sel = selectedWallpaper == preset.mediaUrl ||
                      (selectedWallpaper != null && (
                          selectedWallpaper.contains(preset.id) ||
                          selectedWallpaper.contains(presetHash) ||
                          (!preset.isVideo && selectedWallpaper.lowercase().contains(".gif"))
                      ))
                // GIFs animate in the tile; videos always use their static thumbUrl for instant load
                val tileModel = if (!preset.isVideo) {
                    remember(preset.mediaUrl) {
                        ImageRequest.Builder(context)
                            .data(preset.mediaUrl)
                            .decoderFactory(
                                if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory()
                                else GifDecoder.Factory()
                            )
                            .crossfade(true)
                            .build()
                    }
                } else {
                    // Always use the pre-defined static thumbnail — avoids downloading the
                    // full video just to extract a frame, which was causing ~30 s blank cards.
                    preset.thumbUrl
                }
                WallpaperTile(
                    model = tileModel,
                    isSelected = sel,
                    accent = accent,
                    cardColor = cardColor,
                    cardBorder = cardBorder,
                    onClick = { viewModel.downloadAndSetWallpaper(context, preset.mediaUrl) }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (isDownloadingThis) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).align(Alignment.Center),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (preset.isVideo) Icons.Rounded.PlayArrow else Icons.Rounded.Gif,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = preset.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Static Wallpaper Library ─────────────────────────────────────
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(id = R.string.wallpaper_static_section), color = textPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 2.dp))
            }

            // ── No wallpaper tile ───────────────────────────────────────────
            item {
                val sel = selectedWallpaper == null || selectedWallpaper == "null" || selectedWallpaper.isEmpty()
                WallpaperTile(
                    model = null, isSelected = sel, accent = accent, cardColor = cardColor, cardBorder = cardBorder,
                    onClick = { viewModel.saveBrowserWallpaperUri(context, null) }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.HideImage, null, tint = textSecondary, modifier = Modifier.size(28.dp))
                            Text(stringResource(id = R.string.wallpaper_none), color = textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Active custom wallpaper ─────────────────────────────────────
            val isPresetLive = com.rebelroot.omni.settings.LIVE_ANIMATED_WALLPAPERS.any {
                it.mediaUrl == selectedWallpaper || it.id == selectedWallpaper ||
                (selectedWallpaper != null && selectedWallpaper.contains(
                    java.security.MessageDigest.getInstance("MD5")
                        .digest(it.mediaUrl.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                ))
            }
            val isGalleryFile = selectedWallpaper?.startsWith("file://") == true &&
                    selectedWallpaper?.contains("/wallpapers/") == true
            val isCustomActive = isActive && !PRESET_WALLPAPERS.contains(selectedWallpaper) && !isPresetLive && !isGalleryFile
            if (isCustomActive) {
                item {
                    WallpaperTile(model = selectedWallpaper, isSelected = true, accent = accent,
                        cardColor = cardColor, cardBorder = cardBorder, onClick = { onEditWallpaper(selectedWallpaper!!) })
                }
            }

            // ── Preset tiles ────────────────────────────────────────────────
            items(PRESET_WALLPAPERS) { uri ->
                val sel = selectedWallpaper == uri
                WallpaperTile(
                    model = uri, isSelected = sel, accent = accent,
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
            generateCategoryWallpapers(WALLPAPER_CATEGORIES[selectedCategoryIndex], 60)
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
                    title = { Text(stringResource(R.string.wallpaper_gallery_title), fontWeight = FontWeight.Bold) },
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
                    placeholder = { Text(stringResource(R.string.wallpaper_search_hint), fontSize = 14.sp) },
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
                    itemsIndexed(WALLPAPER_CATEGORIES) { index, key ->
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
                                wallpaperCategoryLabel(key),
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
        val downloadFailedText = stringResource(R.string.wallpaper_download_failed)
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
                        viewModel.downloadAndSetWallpaper(context, wp.fullUrl)
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
                                Toast.makeText(context, downloadFailedText, Toast.LENGTH_SHORT).show()
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

        val isVid = isVideoWallpaperUri(wallpaper.fullUrl)
        val isGif = isGifWallpaperUri(wallpaper.fullUrl)
        if (isVid || isGif) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isVid) Icons.Rounded.PlayArrow else Icons.Rounded.Gif,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isVid) stringResource(R.string.wallpaper_badge_live) else stringResource(R.string.wallpaper_badge_gif),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

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
                Text(stringResource(R.string.wallpaper_tile_hint), color = Color.White.copy(0.55f), fontSize = 9.sp)
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
                    Text(stringResource(R.string.wallpaper_downloading), color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─── Library Tile (preset/custom) ───────────────────────────────────────────
@Composable
private fun WallpaperTile(
    model: Any?,
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
        if (model != null) {
            AsyncImage(
                model = model,
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

    // Use LaunchedEffect to avoid race with DataStore load (Fix #4)
    var tempScale by remember { mutableStateOf(1.0f) }
    var tempOffsetX by remember { mutableStateOf(0f) }
    var tempOffsetY by remember { mutableStateOf(0f) }
    var tempDim by remember { mutableStateOf(0.4f) }
    var tempBlur by remember { mutableStateOf(0f) }
    val editorReady = remember { mutableStateOf(false) }

    LaunchedEffect(isEditingCurrent) {
        if (isEditingCurrent && viewModel.wallpaperScale > 0f) {
            tempScale = viewModel.wallpaperScale
            tempOffsetX = viewModel.wallpaperOffsetX
            tempOffsetY = viewModel.wallpaperOffsetY
            tempDim = if (viewModel.wallpaperDim >= 0f) viewModel.wallpaperDim else 0.4f
            tempBlur = viewModel.wallpaperBlur
        }
        editorReady.value = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallpaper_customize_title), fontWeight = FontWeight.Bold, color = textPrimaryColor) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.wallpaper_close_cd), tint = textPrimaryColor)
                    }
                },
                actions = {
                    TextButton(onClick = { onApply(uri, tempScale, tempOffsetX, tempOffsetY, tempDim, tempBlur) }) {
                        Text(stringResource(R.string.wallpaper_editor_apply), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    val isVid = isVideoWallpaperUri(uri)
                    val isGif = isGifWallpaperUri(uri)
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = tempScale; scaleY = tempScale; translationX = tempOffsetX; translationY = tempOffsetY }
                            .then(if (tempBlur > 0f) Modifier.blur(tempBlur.dp).graphicsLayer() else Modifier)
                    ) {
                        when {
                            isVid -> {
                                com.rebelroot.omni.settings.LIVE_ANIMATED_WALLPAPERS.find { it.mediaUrl == uri }?.thumbUrl?.let {
                                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(0.7f),
                                    modifier = Modifier.size(48.dp).align(Alignment.Center))
                            }
                            isGif -> {
                                val context = LocalContext.current
                                val gifRequest = remember(uri) {
                                    ImageRequest.Builder(context)
                                        .data(uri)
                                        .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                                        .build()
                                }
                                AsyncImage(model = gifRequest, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            else -> {
                                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tempDim)))
                    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures { change, drag -> change.consume(); tempOffsetX += drag.x; tempOffsetY += drag.y }
                    })
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .background(Color.Black.copy(0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(stringResource(R.string.wallpaper_drag_reposition), color = Color.White.copy(0.9f), fontSize = 9.sp)
                    }
                }
            }

            // Controls
            Surface(color = cardColor, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SliderRow(stringResource(R.string.wallpaper_zoom_scale), String.format("%.1fx", tempScale), tempScale, 1f..3f, accentColor, textPrimaryColor, textSecondaryColor) { tempScale = it }
                    SliderRow(stringResource(R.string.wallpaper_blur_amount), "${tempBlur.toInt()} dp", tempBlur, 0f..25f, accentColor, textPrimaryColor, textSecondaryColor) { tempBlur = it }
                    SliderRow(stringResource(R.string.wallpaper_dim_opacity), "${(tempDim * 100).toInt()}%", tempDim, 0f..0.9f, accentColor, textPrimaryColor, textSecondaryColor) { tempDim = it }
                    Button(
                        onClick = { tempScale = 1f; tempOffsetX = 0f; tempOffsetY = 0f },
                        colors = ButtonDefaults.buttonColors(containerColor = cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.wallpaper_reset_cd), tint = textPrimaryColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wallpaper_reset_position), color = textPrimaryColor)
                    }
                }
            }
        }
    }
}
