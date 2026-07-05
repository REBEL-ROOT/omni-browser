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

package com.rebelroot.omni.media.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.rememberCoroutineScope
import com.rebelroot.omni.media.MediaInterceptor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPath: String,
    referrerUrl: String = "",
    videoTitle: String = "",
    downloadEngine: com.rebelroot.omni.media.StreamDownloadEngine? = null,
    viewModel: com.rebelroot.omni.browser.BrowserViewModel? = null,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val accentColor = if (viewModel != null) MaterialTheme.colorScheme.primary else Color(0xFF0088FF)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val decodedPath = remember(videoPath) {
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://") || videoPath.startsWith("/")) {
            videoPath
        } else {
            try {
                val decodedBytes = try {
                    android.util.Base64.decode(videoPath, android.util.Base64.URL_SAFE)
                } catch (e: Exception) {
                    android.util.Base64.decode(videoPath, android.util.Base64.DEFAULT)
                }
                String(decodedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                try {
                    java.net.URLDecoder.decode(videoPath, "UTF-8")
                } catch (e2: Exception) {
                    videoPath
                }
            }
        }
    }
    val isOnline = remember(decodedPath) { decodedPath.startsWith("http://") || decodedPath.startsWith("https://") }
    var downloadToLocker by remember { mutableStateOf(false) }
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    var exoPlayerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableLongStateOf(viewModel?.getVideoPosition(decodedPath) ?: 0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Gestures overlay states
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var gestureIndicatorText by remember { mutableStateOf("") }

    var showControls by remember { mutableStateOf(true) }

    // Aspect ratio mode: fit (letterbox) vs fill (crop to screen)
    var isFillMode by remember { mutableStateOf(false) }

    // Downloader Quality Selector States
    var isFetchingQualities by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var qualityOptions by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }

    val jobs by downloadEngine?.jobs?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val currentJob = remember(jobs, decodedPath) {
        jobs.find { it.url == decodedPath }
    }
    val progressState = currentJob?.progress?.collectAsState()

    // Initialize brightness to current system level if available
    LaunchedEffect(Unit) {
        activity?.window?.attributes?.screenBrightness?.let {
            if (it >= 0f) brightness = it
        }
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        volume = curVol / maxVol
    }

    // Auto-fade controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Manage fullscreen and system bars visibility
    LaunchedEffect(showControls) {
        activity?.let {
            com.rebelroot.omni.browser.FullscreenManager.setFullscreen(it, !showControls)
        }
    }

    // Clean up orientation and fullscreen states when leaving the player
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                com.rebelroot.omni.browser.FullscreenManager.setFullscreen(it, false)
            }
        }
    }

    DisposableEffect(decodedPath, referrerUrl) {
        var exoPlayer: ExoPlayer? = null
        var lifecycleObserver: androidx.lifecycle.LifecycleEventObserver? = null

        try {
            val uri = if (decodedPath.startsWith("http://") || decodedPath.startsWith("https://")) {
                Uri.parse(decodedPath)
            } else {
                Uri.fromFile(java.io.File(decodedPath))
            }

            // Configure network data source with custom headers to bypass host protections
            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")

            android.util.Log.d("VideoPlayer", "🎬 VideoPlayerScreen: decodedPath = $decodedPath, referrerUrl = $referrerUrl")
            if (referrerUrl.isNotEmpty() && !isDirectVideoUrl(referrerUrl)) {
                android.util.Log.d("VideoPlayer", "🎬 Setting Referer header to: $referrerUrl")
                httpDataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to referrerUrl))
            } else {
                android.util.Log.d("VideoPlayer", "🎬 Referer header NOT set. referrerUrl = $referrerUrl")
            }

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            val urlLower = decodedPath.lowercase()
            if (urlLower.contains(".m3u8") || urlLower.contains("m3u8") || urlLower.contains("/hls/")) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            } else if (urlLower.contains(".mpd") || urlLower.contains("mpd")) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
            } else if (urlLower.contains("index.php") || urlLower.contains(".php")) {
                if (!urlLower.contains(".mp4") && !urlLower.contains(".mkv") && !urlLower.contains(".webm") && !urlLower.contains(".avi") && !urlLower.contains(".mov")) {
                    android.util.Log.d("VideoPlayer", "🎬 PHP stream URL detected without progressive extension; setting mimeType to HLS (M3U8)")
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }
            }
            val mediaItem = mediaItemBuilder.build()

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    setMediaItem(mediaItem)

                    val initialPos = viewModel?.getVideoPosition(decodedPath) ?: 0L
                    if (initialPos > 0L) seekTo(initialPos)

                    repeatMode = if (viewModel?.isPlayerLoopEnabled == true) {
                        Player.REPEAT_MODE_ONE
                    } else {
                        Player.REPEAT_MODE_OFF
                    }

                    val currentParams = trackSelectionParameters
                    val updatedParams = when (viewModel?.playerDefaultQuality) {
                        "360p" -> currentParams.buildUpon().setMaxVideoSize(640, 360).build()
                        "480p" -> currentParams.buildUpon().setMaxVideoSize(854, 480).build()
                        "720p" -> currentParams.buildUpon().setMaxVideoSize(1280, 720).build()
                        "1080p" -> currentParams.buildUpon().setMaxVideoSize(1920, 1080).build()
                        else -> currentParams
                    }
                    trackSelectionParameters = updatedParams

                    prepare()
                    playWhenReady = viewModel?.isPlayerAutoPlayEnabled ?: true

                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                duration = this@apply.duration
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            android.util.Log.e("VideoPlayer", "ExoPlayer playback error: ${error.message}", error)
                            coroutineScope.launch {
                                Toast.makeText(context, "${context.getString(R.string.video_player_playback_error)}: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                }

            exoPlayer = player
            exoPlayerInstance = player

            var wasPlayingBeforePause = false
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    if (viewModel?.isPlayerBackgroundPlaybackEnabled == false) {
                        wasPlayingBeforePause = player.playWhenReady
                        player.playWhenReady = false
                    }
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    if (viewModel?.isPlayerBackgroundPlaybackEnabled == false && wasPlayingBeforePause) {
                        player.playWhenReady = true
                    }
                }
            }
            lifecycleObserver = observer
            lifecycleOwner.lifecycle.addObserver(observer)

        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Failed to initialize ExoPlayer: ${e.message}", e)
            coroutineScope.launch {
                Toast.makeText(context, "Unable to play video: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                kotlinx.coroutines.delay(500)
                onNavigateBack()
            }
        }

        onDispose {
            try {
                lifecycleObserver?.let { lifecycleOwner.lifecycle.removeObserver(it) }
                exoPlayer?.let { player ->
                    viewModel?.saveVideoPosition(decodedPath, player.currentPosition)
                    player.release()
                }
                exoPlayerInstance = null
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayer", "Error during ExoPlayer dispose: ${e.message}")
            }
        }
    }

    // Progress updates tracking
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            exoPlayerInstance?.let {
                playbackPosition = it.currentPosition
                viewModel?.saveVideoPosition(decodedPath, it.currentPosition)
            }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer Canvas Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Prevent the native PlayerView from intercepting touch events;
                    // all touch handling is done by the Compose overlay layer.
                    isClickable = false
                    isFocusable = false
                    resizeMode = if (isFillMode) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayerInstance
                playerView.resizeMode = if (isFillMode) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        // Volume and Brightness Drag Interceptors
        if (!showControls) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Half: Brightness
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            if (viewModel?.isPlayerBrightnessGestureEnabled != false) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        showGestureIndicator = true
                                    },
                                    onDragEnd = {
                                        showGestureIndicator = false
                                    }
                                ) { _, dragAmount ->
                                    // Adjust brightness
                                    brightness = (brightness - dragAmount / 1500f).coerceIn(0f, 1f)
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = brightness
                                        act.window.attributes = lp
                                    }
                                    gestureIndicatorText = "🔆 Brightness: ${(brightness * 100).toInt()}%"
                                }
                            }
                        }
                )

                // Right Half: Volume
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            if (viewModel?.isPlayerVolumeGestureEnabled != false) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        showGestureIndicator = true
                                    },
                                    onDragEnd = {
                                        showGestureIndicator = false
                                    }
                                ) { _, dragAmount ->
                                    // Adjust audio
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    volume = (volume - dragAmount / 1500f).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (volume * maxVol).toInt(),
                                        0
                                    )
                                    gestureIndicatorText = "🔊 Volume: ${(volume * 100).toInt()}%"
                                }
                            }
                        }
                )
            }
        }

        // Swipe / Drag HUD indicator popup
        AnimatedVisibility(
            visible = showGestureIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = gestureIndicatorText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical =10.dp)
                )
            }
        }

        // Always-visible back button — outside AnimatedVisibility so it's always accessible
        // even when controls are faded out. Only shown when the full controls overlay is hidden.
        AnimatedVisibility(
            visible = !showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.45f)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                }
        }

        // Tap-to-toggle-controls layer — sits above the AndroidView but below
        // the controls overlay, so taps reach here only when controls are not
        // consuming the event. Supports double-tap to toggle video aspect ratio.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            isFillMode = !isFillMode
                            gestureIndicatorText = if (isFillMode) "Aspect Ratio: Fill Screen" else "Aspect Ratio: Fit to Screen"
                            showGestureIndicator = true
                            coroutineScope.launch {
                                delay(1500)
                                showGestureIndicator = false
                            }
                        },
                        onTap = {
                            showControls = !showControls
                        }
                    )
                }
        )

        // Elegant Media Playback Overlays
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Transparent scrim background that is clickable to dismiss controls
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = false
                        }
                )

                // 2. Interactive Controls Layer (Header, Middle, Bottom)
                // This is a sibling to the scrim Box and sits on top. Since this Box itself has no
                // clickable modifier, click events on empty spaces pass through to the scrim Box beneath.
                // Buttons and sliders consume clicks directly, preventing any interference.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    // Header (Back + Name + Controls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        val displayName = remember(decodedPath, videoTitle) {
                            if (videoTitle.isNotEmpty()) {
                                videoTitle
                            } else if (decodedPath.startsWith("http://") || decodedPath.startsWith("https://")) {
                                Uri.parse(decodedPath).lastPathSegment ?: "Online Stream"
                            } else {
                                java.io.File(decodedPath).name
                            }
                        }
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Screen orientation toggle (Enter / Exit Fullscreen)
                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        IconButton(
                            onClick = {
                                activity?.let { act ->
                                    act.requestedOrientation = if (isLandscape) {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isLandscape) accentColor.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (isLandscape) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // PiP Trigger Button
                        Button(
                            onClick = {
                                if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity.enterPictureInPictureMode(params)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(stringResource(R.string.pip_mode), color = Color.White, fontSize = 14.sp)
                        }

                        if (isOnline && downloadEngine != null && currentJob == null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isFetchingQualities = true
                                        val parsed = fetchVideoQualities(decodedPath)
                                        isFetchingQualities = false
                                        
                                        if (decodedPath.contains(".m3u8") && parsed.size <= 2 && parsed.any { it.label.contains("Source Stream") }) {
                                            Toast.makeText(context, context.getString(R.string.video_player_m3u8_fallback), Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        qualityOptions = parsed
                                        showQualitySelector = true
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.downloads_title),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Middle: Play/Pause/Rewind/Forward Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                exoPlayerInstance?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0L)) }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("◀◀", color = Color.White, fontSize = 11.sp)
                        }

                        // Play/Pause Toggle
                        IconButton(
                            onClick = {
                                exoPlayerInstance?.let {
                                    if (isPlaying) it.pause() else it.play()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                exoPlayerInstance?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(duration)) }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text("▶▶", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    // Bottom Seek Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(playbackPosition), color = Color.White, fontSize = 12.sp)
                            Text(formatDuration(duration), color = Color.White, fontSize = 12.sp)
                        }
                        Slider(
                            value = if (duration > 0) playbackPosition.toFloat() / duration else 0f,
                            onValueChange = { percent ->
                                exoPlayerInstance?.seekTo((percent * duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // 1. Quality fetching loading spinner
        if (isFetchingQualities) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF0D1620),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(0.5.dp, Color(0xFF16222F)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = accentColor)
                        Text(
                            text = "Analyzing Stream Qualities...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 2. Beautiful quality selector alert dialog
        if (showQualitySelector && downloadEngine != null) {
            val suggestedName = remember(decodedPath, videoTitle) {
                if (videoTitle.isNotEmpty()) {
                    videoTitle
                } else {
                    val lastSeg = Uri.parse(decodedPath).lastPathSegment
                    if (!lastSeg.isNullOrBlank() && lastSeg.contains(".")) {
                        lastSeg.substringBeforeLast(".")
                    } else {
                        "Video_${System.currentTimeMillis()}"
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showQualitySelector = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.video_player_select_quality),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.video_player_download_desc),
                            color = Color(0xFF8E9AA8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        
                        // Switch for saving to secure private vault locker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF16222F))
                                .border(BorderStroke(0.5.dp, Color(0xFF23374A)), RoundedCornerShape(10.dp))
                                .clickable { downloadToLocker = !downloadToLocker }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (downloadToLocker) accentColor else Color(0xFF8E9AA8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.video_player_save_to_locker),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.video_player_encrypt_desc),
                                        color = Color(0xFF8E9AA8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = downloadToLocker,
                                onCheckedChange = { downloadToLocker = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = Color(0xFF8E9AA8),
                                    uncheckedTrackColor = Color(0xFF070A0F)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        qualityOptions.forEach { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showQualitySelector = false
                                        val mediaType = when {
                                            option.isAudioOnly -> MediaInterceptor.MediaType.AUDIO
                                            option.url.contains(".m3u8") -> MediaInterceptor.MediaType.HLS
                                            option.url.contains(".mpd") -> MediaInterceptor.MediaType.DASH
                                            else -> MediaInterceptor.MediaType.MP4
                                        }
                                        coroutineScope.launch {
                                            downloadEngine.startDownload(
                                                url = option.url,
                                                suggestedName = "${suggestedName}_${option.label}",
                                                type = mediaType,
                                                saveToLocker = downloadToLocker
                                            )
                                            val toastMsg = if (downloadToLocker) context.getString(R.string.video_player_queued_locker) else context.getString(R.string.video_player_queued_download, option.label)
                                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF16222F),
                                border = BorderStroke(0.5.dp, Color(0xFF23374A))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (option.isAudioOnly) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                                            contentDescription = null,
                                            tint = if (option.isAudioOnly) Color(0xFFFF9800) else accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = option.label,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = stringResource(R.string.downloads_title),
                                        tint = Color(0xFF8E9AA8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    OutlinedButton(
                        onClick = { showQualitySelector = false },
                        border = BorderStroke(0.5.dp, Color(0xFF16222F)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E9AA8)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel_text), fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = Color(0xFF0D1620),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(BorderStroke(0.5.dp, Color(0xFF16222F)), RoundedCornerShape(16.dp))
            )
        }

        // Progress Overlay (Visible for active downloads)
        if (isOnline && downloadEngine != null && currentJob != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 16.dp)
            ) {
                // Download Progress / Status Overlay Card
                val progressValue = progressState?.value
                Surface(
                    modifier = Modifier.width(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D1620).copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, Color(0xFF16222F))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.video_player_downloading),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel_text),
                                tint = Color(0xFF8E9AA8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        downloadEngine.cancelDownload(currentJob.id)
                                        Toast.makeText(context, context.getString(R.string.video_player_download_cancelled), Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }

                        when (progressValue) {
                            is com.rebelroot.omni.media.StreamDownloadEngine.DownloadProgress.Downloading -> {
                                val percent = progressValue.percent
                                val percentText = if (percent >= 0) "$percent%" else stringResource(R.string.video_player_downloading) + "..."
                                val sizeMb = progressValue.bytesDownloaded.toFloat() / (1024 * 1024)
                                
                                Text(
                                    text = "$percentText (${String.format("%.1f", sizeMb)} MB)",
                                    color = Color(0xFF8E9AA8),
                                    fontSize = 10.sp
                                )
                                if (percent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = accentColor,
                                        trackColor = Color(0xFF16222F)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = accentColor,
                                        trackColor = Color(0xFF16222F)
                                    )
                                }
                            }
                            is com.rebelroot.omni.media.StreamDownloadEngine.DownloadProgress.Muxing -> {
                                Text(
                                    text = progressValue.message,
                                    color = Color(0xFFFF9800),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = Color(0xFFFF9800),
                                    trackColor = Color(0xFF16222F)
                                )
                            }
                            is com.rebelroot.omni.media.StreamDownloadEngine.DownloadProgress.Complete -> {
                                Text(
                                    text = stringResource(R.string.video_player_download_complete),
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                LaunchedEffect(Unit) {
                                    delay(2500)
                                    downloadEngine.cancelDownload(currentJob.id)
                                }
                            }
                            is com.rebelroot.omni.media.StreamDownloadEngine.DownloadProgress.Error -> {
                                Text(
                                    text = progressValue.message,
                                    color = Color(0xFFF44336),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                            null -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = accentColor,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / (1000 * 60)) % 60
    val hr = (millis / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

private fun isDirectVideoUrl(url: String): Boolean {
    val clean = url.trim().lowercase()
    return clean.endsWith(".mp4") ||
            clean.endsWith(".m3u8") ||
            clean.endsWith(".mpd") ||
            clean.endsWith(".webm") ||
            clean.endsWith(".mkv") ||
            clean.endsWith(".ts") ||
            clean.contains(".mp4?") ||
            clean.contains(".m3u8?") ||
            clean.contains(".mpd?") ||
            clean.contains(".webm?") ||
            clean.contains(".mkv?") ||
            clean.contains(".ts?")
}

data class VideoQualityOption(
    val label: String,
    val url: String,
    val isAudioOnly: Boolean = false
)

private suspend fun fetchVideoQualities(streamUrl: String): List<VideoQualityOption> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val options = mutableListOf<VideoQualityOption>()
    
    if (!streamUrl.contains(".m3u8")) {
        options.add(VideoQualityOption("Source HD (Original)", streamUrl))
        options.add(VideoQualityOption("Extract Audio (MP3)", streamUrl, isAudioOnly = true))
        return@withContext options
    }
    
    try {
        val connection = java.net.URL(streamUrl).openConnection() as java.net.HttpURLConnection
        connection.connect()
        val manifestContent = connection.inputStream.bufferedReader().use { it.readText() }
        
        val lines = manifestContent.lines()
        var currentResolution = ""
        val baseUri = streamUrl.substring(0, streamUrl.lastIndexOf("/") + 1)
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(trimmed)
                if (resMatch != null) {
                    val res = resMatch.groupValues[1]
                    val height = res.substringAfter("x").toIntOrNull() ?: 0
                    currentResolution = "${height}p"
                } else {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(trimmed)
                    if (bwMatch != null) {
                        val kbps = (bwMatch.groupValues[1].toIntOrNull() ?: 0) / 1000
                        currentResolution = "${kbps}kbps"
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentResolution.isNotEmpty()) {
                val fullUrl = if (trimmed.startsWith("http")) trimmed else "$baseUri$trimmed"
                options.add(VideoQualityOption(currentResolution, fullUrl))
                currentResolution = ""
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoPlayer", "Failed to parse HLS variants in player", e)
    }
    
    if (options.isEmpty()) {
        options.add(VideoQualityOption("Source Stream (Auto)", streamUrl))
    }
    options.add(VideoQualityOption("Extract Audio (MP3)", streamUrl, isAudioOnly = true))
    
    return@withContext options.distinctBy { it.label }
}
