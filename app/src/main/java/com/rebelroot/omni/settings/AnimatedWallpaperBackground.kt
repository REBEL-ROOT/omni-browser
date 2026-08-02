/*
 * Omni Browser - Premium Animated & Video Wallpaper Engine
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest

data class AnimatedWallpaperPreset(
    val id: String,
    val title: String,
    val category: String,
    val thumbUrl: String,
    val mediaUrl: String,
    val isVideo: Boolean = false
)

val LIVE_ANIMATED_WALLPAPERS = listOf(
    AnimatedWallpaperPreset(
        id = "cyber_city",
        title = "Cyberpunk Night",
        category = "Sci-Fi",
        thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-futuristic-city-with-bright-neon-lights-at-night-41554-large.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "rainy_window",
        title = "Rainy City Window",
        category = "Relaxing",
        thumbUrl = "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-raindrops-on-a-window-pane-at-night-41549-large.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "neon_waves",
        title = "Neon Fluid Waves",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-digital-animation-of-screens-with-graphs-and-data-41536-large.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "space_cosmos",
        title = "Deep Space Cosmos",
        category = "Space",
        thumbUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-stars-in-the-night-sky-4040-large.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "ocean_breeze",
        title = "Calm Ocean Waves",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-sea-waves-approaching-the-sand-41530-large.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "matrix_rain",
        title = "Matrix Digital Code",
        category = "Sci-Fi",
        thumbUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop",
        mediaUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExdW9uaXZ4OWYwNGd1bmM1c3Q4ZGFzeXJ4NnM2azE5enptcW5vdDVzdiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/L1R1tvI9svkIWwpVYr/giphy.gif",
        isVideo = false
    ),
    AnimatedWallpaperPreset(
        id = "lofi_cafe",
        title = "Lo-Fi Coffee Shop",
        category = "Lo-Fi",
        thumbUrl = "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600&auto=format&fit=crop",
        mediaUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHpucjNlOWRxeWFvZmVudDFndDFjcHZtZ3d1NHEycDdmcDduYmsyeCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/d480lR5b5h8k9X0M/giphy.gif",
        isVideo = false
    ),
    AnimatedWallpaperPreset(
        id = "foggy_forest",
        title = "Foggy Mountain Pines",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=600&auto=format&fit=crop",
        mediaUrl = "https://assets.mixkit.co/videos/preview/mixkit-sun-rays-through-the-trees-in-a-forest-41537-large.mp4",
        isVideo = true
    )
)

fun isVideoWallpaperUri(uri: String?): Boolean {
    if (uri == null || uri.trim().isEmpty()) return false
    val lower = uri.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
            lower.endsWith(".mkv") || lower.endsWith(".mov") ||
            lower.contains("video/") || lower.contains(".mp4?") ||
            lower.contains(".webm?") || lower.startsWith("content://") ||
            (lower.startsWith("file://") && (lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") || lower.contains(".mov")))
}

fun isGifWallpaperUri(uri: String?): Boolean {
    if (uri == null || uri.trim().isEmpty()) return false
    val lower = uri.lowercase()
    return lower.endsWith(".gif") || lower.contains(".gif?") || lower.contains("giphy.com") ||
            (lower.startsWith("file://") && lower.contains(".gif"))
}

@Composable
fun AnimatedWallpaperBackground(
    wallpaperUri: String,
    scale: Float = 1.0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    blur: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedPreset = remember(wallpaperUri) {
        LIVE_ANIMATED_WALLPAPERS.find { it.mediaUrl == wallpaperUri || it.id == wallpaperUri }
    }
    val actualMediaUrl = resolvedPreset?.mediaUrl ?: wallpaperUri
    val isVideo = remember(actualMediaUrl) {
        resolvedPreset?.isVideo ?: isVideoWallpaperUri(actualMediaUrl)
    }
    val isGif = remember(actualMediaUrl) {
        !isVideo && (resolvedPreset?.isVideo == false || isGifWallpaperUri(actualMediaUrl))
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!isVideo) {
            val fallbackModel = resolvedPreset?.thumbUrl ?: actualMediaUrl
            AsyncImage(
                model = fallbackModel,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .then(
                        if (blur > 0f) Modifier.blur(blur.dp).graphicsLayer() else Modifier
                    )
            )
        }

        when {
            isVideo -> {
                VideoWallpaperPlayer(
                    wallpaperUri = actualMediaUrl,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    blur = blur
                )
            }
            isGif -> {
                val imageRequest = remember(actualMediaUrl, context) {
                    ImageRequest.Builder(context)
                        .data(actualMediaUrl)
                        .decoderFactory(
                            if (Build.VERSION.SDK_INT >= 28) {
                                ImageDecoderDecoder.Factory()
                            } else {
                                GifDecoder.Factory()
                            }
                        )
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .then(
                            if (blur > 0f) Modifier.blur(blur.dp).graphicsLayer() else Modifier
                        )
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoWallpaperPlayer(
    wallpaperUri: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    blur: Float
) {
    val context = LocalContext.current

    val exoPlayer = remember(wallpaperUri) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                setMediaItem(MediaItem.fromUri(Uri.parse(wallpaperUri)))
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("AnimatedWallpaper", "ExoPlayer playback error: ${error.message}", error)
                        prepare()
                        playWhenReady = true
                    }
                })
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        exoPlayer.setVideoTextureView(this@apply)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        exoPlayer.clearVideoTextureView(this@apply)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
                if (isAvailable) {
                    exoPlayer.setVideoTextureView(this)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .then(
                if (blur > 0f) Modifier.blur(blur.dp).graphicsLayer() else Modifier
            )
    )
}
