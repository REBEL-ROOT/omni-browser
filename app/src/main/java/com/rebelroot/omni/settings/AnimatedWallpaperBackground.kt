/*
 * Omni Browser - Premium Animated & Video Wallpaper Engine
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.settings

import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.media3.exoplayer.hls.HlsMediaSource
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
        id = "ocean_pexels",
        title = "Calm Ocean Waves",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/5853147/5853147-hd_2048_1080_30fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "sunset_pexels",
        title = "Sunset Horizon",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1506815444479-bfdb1e96c566?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/11335978/11335978-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "sunlight_pexels",
        title = "Sunlight Through Trees",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/11265968/11265968-hd_1920_1080_25fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "city_pexels",
        title = "City Skyscrapers",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/12685044/12685044-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "rainy_night_pexels",
        title = "Rainy Night City",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/855432/855432-hd_1840_1034_25fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "abstract_pexels",
        title = "Abstract Colors",
        category = "Abstract",
        thumbUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/10881637/10881637-hd_1920_1080_25fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "plants_pexels",
        title = "Plants by River",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/1208094/1208094-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "woods_pexels",
        title = "Walking in Woods",
        category = "Nature",
        thumbUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600&auto=format&fit=crop",
        mediaUrl = "https://videos.pexels.com/video-files/8424070/8424070-hd_1920_1080_30fps.mp4",
        isVideo = true
    ),
    AnimatedWallpaperPreset(
        id = "pinterest_hls",
        title = "Pinterest City Clip",
        category = "City",
        thumbUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop",
        mediaUrl = "https://v1.pinimg.com/videos/iht/hls/d0/b6/93/d0b69328b1f41ad0271fe4374baa688b.m3u8",
        isVideo = true
    ),
    // Working Giphy GIF presets
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
    )
)

fun isVideoWallpaperUri(uri: String?): Boolean {
    if (uri == null || uri.trim().isEmpty()) return false
    val lower = uri.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
            lower.endsWith(".mkv") || lower.endsWith(".mov") ||
            lower.endsWith(".m3u8") ||
            lower.contains("video/") || lower.contains(".mp4?") ||
            lower.contains(".webm?") || lower.contains(".m3u8?") ||
            lower.startsWith("content://") ||
            (lower.startsWith("file://") && (lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") || lower.contains(".mov")))
}

fun isGifWallpaperUri(uri: String?): Boolean {
    if (uri == null || uri.trim().isEmpty()) return false
    val lower = uri.lowercase()
    return lower.endsWith(".gif") || lower.contains(".gif?") || lower.contains("giphy.com") ||
            (lower.startsWith("file://") && lower.contains(".gif"))
}

/** Resolve a live preset from any URI — remote URL, file:// path, or preset id.
 *  For downloaded files the MD5 hash of the original URL is embedded in the filename. */
fun resolveLivePreset(uri: String?): AnimatedWallpaperPreset? {
    if (uri.isNullOrBlank()) return null
    // Direct match on mediaUrl or id
    LIVE_ANIMATED_WALLPAPERS.find { it.mediaUrl == uri || it.id == uri }?.let { return it }
    // File-based match: downloaded files are named <md5>.ext
    if (uri.startsWith("file://")) {
        LIVE_ANIMATED_WALLPAPERS.forEach { preset ->
            val hash = java.security.MessageDigest.getInstance("MD5")
                .digest(preset.mediaUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
            if (uri.contains(hash)) return preset
        }
    }
    return null
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
    val resolvedPreset = remember(wallpaperUri) { resolveLivePreset(wallpaperUri) }
    val actualMediaUrl = resolvedPreset?.mediaUrl ?: wallpaperUri
    val isVideo = remember(actualMediaUrl) {
        resolvedPreset?.isVideo ?: isVideoWallpaperUri(actualMediaUrl)
    }
    val isGif = remember(actualMediaUrl) {
        !isVideo && (resolvedPreset?.isVideo == false || isGifWallpaperUri(actualMediaUrl))
    }

    Box(modifier = modifier.fillMaxSize()) {
        var videoFailed by remember(wallpaperUri) { mutableStateOf(false) }

        when {
            isVideo -> {
                // Video is primary — static thumbnail only shows on error
                if (videoFailed) {
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
                                if (blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    Modifier.blur(blur.dp).graphicsLayer()
                                else Modifier
                            )
                    )
                }
                VideoWallpaperPlayer(
                    wallpaperUri = actualMediaUrl,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    blur = blur,
                    onError = { videoFailed = true }
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
                            if (blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                Modifier.blur(blur.dp).graphicsLayer()
                            else Modifier
                        )
                )
            }
            else -> {
                // Static wallpaper — show the image directly
                AsyncImage(
                    model = actualMediaUrl,
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
                            if (blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                Modifier.blur(blur.dp).graphicsLayer()
                            else Modifier
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
    blur: Float,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val maxRetries = 3
    var errorCount by remember(wallpaperUri) { mutableStateOf(0) }

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
                        errorCount++
                        Log.e("AnimatedWallpaper", "ExoPlayer error $errorCount/$maxRetries: ${error.message}")
                        if (errorCount >= maxRetries) {
                            Log.w("AnimatedWallpaper", "Too many playback errors, stopping retries")
                            playWhenReady = false
                            stop()
                            onError()
                        } else {
                            prepare()
                            playWhenReady = true
                        }
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
            TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
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
                if (blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    Modifier.blur(blur.dp).graphicsLayer()
                else Modifier
            )
    )
}
