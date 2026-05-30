package com.omni.browser.media.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
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

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPath: String,
    videoTitle: String = "",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var exoPlayerInstance by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Gestures overlay states
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var gestureIndicatorText by remember { mutableStateOf("") }

    var showControls by remember { mutableStateOf(true) }

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

    DisposableEffect(videoPath) {
        val uri = if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            Uri.parse(videoPath)
        } else {
            Uri.fromFile(java.io.File(videoPath))
        }
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration = this@apply.duration
                    }
                }
            })
        }
        exoPlayerInstance = exoPlayer

        onDispose {
            exoPlayer.release()
        }
    }

    // Progress updates tracking
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            exoPlayerInstance?.let {
                playbackPosition = it.currentPosition
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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayerInstance
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { showControls = !showControls }
        )

        // Volume and Brightness Drag Interceptors
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Brightness
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
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
            )

            // Right Half: Volume
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
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
            )
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Elegant Media Playback Overlays
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Header (Back + Name)
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
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val displayName = remember(videoPath, videoTitle) {
                        if (videoTitle.isNotEmpty()) {
                            videoTitle
                        } else if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                            Uri.parse(videoPath).lastPathSegment ?: "Online Stream"
                        } else {
                            java.io.File(videoPath).name
                        }
                    }
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
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
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("PiP", color = Color.White, fontSize = 12.sp)
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
