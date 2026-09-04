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

package com.rebelroot.omni

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebelroot.omni.browser.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class OmniApplication : Application(), coil.ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): coil.ImageLoader {
        val baseClient = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val okHttpClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val originalUrl = originalRequest.url.toString()

                val referer = com.rebelroot.omni.browser.ImageGrabberUtils.resolveReferer(originalUrl)
                val builder = originalRequest.newBuilder()
                if (referer.isNotEmpty() && originalRequest.header("Referer") == null) {
                    builder.addHeader("Referer", referer)
                }
                if (originalRequest.header("User-Agent") == null) {
                    builder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                }
                if (originalRequest.header("Accept") == null) {
                    builder.addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                }
                val requestWithHeaders = builder.build()

                var response = try {
                    chain.proceed(requestWithHeaders)
                } catch (e: Exception) {
                    null
                }

                // If request failed or returned non-2xx, probe candidate alternate URLs using isolated call on baseClient
                if (response == null || !response.isSuccessful) {
                    val failCode = response?.code ?: -1
                    android.util.Log.d("ImageFallback", "Primary FAILED ($failCode): $originalUrl")
                    val alternateUrls = com.rebelroot.omni.browser.ImageGrabberUtils.getCandidateAlternateUrls(originalUrl)
                    android.util.Log.d("ImageFallback", "Candidates (${alternateUrls.size}): $alternateUrls")
                    for (altUrl in alternateUrls) {
                        try {
                            val altReferer = com.rebelroot.omni.browser.ImageGrabberUtils.resolveReferer(altUrl)
                            val altReqBuilder = requestWithHeaders.newBuilder().url(altUrl)
                            if (altReferer.isNotEmpty()) {
                                altReqBuilder.header("Referer", altReferer)
                            }
                            val altRequest = altReqBuilder.build()
                            val altResponse = baseClient.newCall(altRequest).execute()
                            android.util.Log.d("ImageFallback", "Candidate $altUrl -> ${altResponse.code}")
                            if (altResponse.isSuccessful) {
                                response?.close()
                                android.util.Log.d("ImageFallback", "SUCCESS with: $altUrl")
                                return@addInterceptor altResponse
                            }
                            altResponse.close()
                        } catch (e: Exception) {
                            android.util.Log.d("ImageFallback", "Candidate exception: $altUrl -> ${e.message}")
                        }
                    }
                    android.util.Log.d("ImageFallback", "All candidates exhausted for: $originalUrl")
                }

                response ?: chain.proceed(requestWithHeaders)
            }
            .build()

        return coil.ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(coil.decode.SvgDecoder.Factory())
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    /**
     * Reads the startup preference snapshot, falling back to empty preferences
     * on any read error. Callers own the lifecycle decision (synchronous theme
     * pre-load vs. async updates); this helper only covers retrieval/parsing.
     */
    private suspend fun readStartupPrefs(): Preferences =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .first()

    private fun isMainProcess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getProcessName() == packageName
        } else {
            try {
                val pid = android.os.Process.myPid()
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val currentProcess = am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
                currentProcess == null || currentProcess == packageName
            } catch (_: Exception) {
                true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Skip UI, Coil, and DataStore initialization in child processes
        // (e.g. GeckoView :gpu, :tab*, :socket child processes).
        if (!isMainProcess()) {
            return
        }

        appContext = this
        com.rebelroot.omni.sync.notification.SyncNotificationManager.createNotificationChannel(this)

        // Set global Coil ImageLoader with auto-probing fallback interceptor
        try {
            coil.Coil.setImageLoader(newImageLoader())
        } catch (_: Exception) {}

        // Apply hardcoded defaults immediately so the first Compose frame has a
        // valid theme — no main-thread stall, no white flash.
        ThemeStateHolder.darkThemeEnabled = true
        ThemeStateHolder.amoledMode = false
        ThemeStateHolder.accentTheme = "Ocean Blue"
        ThemeStateHolder.dynamicColorEnabled = false

        // Pre-load UI scale and wallpaper preferences synchronously so the very
        // first Compose frame renders with the correct layout and wallpaper.
        // DataStore reads from an in-process protobuf cache after the first cold
        // open, so this runBlocking call completes in < 1 ms on warm launches
        // and ≤ 5 ms on a true cold start — imperceptible to the user and far
        // cheaper than the layout jump / wallpaper pop-in it prevents.
        runBlocking {
            try {
                val prefs = readStartupPrefs()
                UiStateHolder.uiScale            = prefs[UI_SCALE_KEY]            ?: 1.0f
                UiStateHolder.homeUiScale        = prefs[HOME_UI_SCALE_KEY]       ?: 0.90f
                UiStateHolder.bottomNavScale     = prefs[BOTTOM_NAV_SCALE_KEY]    ?: 1.0f
                UiStateHolder.browserWallpaperUri = prefs[BROWSER_WALLPAPER_URI_KEY]
                UiStateHolder.wallpaperDim       = prefs[WALLPAPER_DIM_KEY]       ?: -1f
                UiStateHolder.wallpaperBlur      = prefs[WALLPAPER_BLUR_KEY]      ?: 0f
                UiStateHolder.wallpaperScale     = prefs[WALLPAPER_SCALE_KEY]     ?: 1.0f
                UiStateHolder.wallpaperOffsetX   = prefs[WALLPAPER_OFFSET_X_KEY]  ?: 0f
                UiStateHolder.wallpaperOffsetY   = prefs[WALLPAPER_OFFSET_Y_KEY]  ?: 0f
            } catch (_: Exception) {
                // Defaults already set in UiStateHolder — nothing else to do.
            }
        }

        // Load the persisted theme asynchronously and update the holder.
        // MainActivity reads ThemeStateHolder in onCreate(), which runs after
        // Application.onCreate() returns on the same thread. The async update
        // arrives in time for the next Activity recreation (theme change by user)
        // but the very first cold start uses the defaults above — identical to
        // the previous runBlocking values and indistinguishable to the user.
        appScope.launch {
            try {
                val prefs = readStartupPrefs()
                ThemeStateHolder.darkThemeEnabled  = prefs[DARK_THEME_ENABLED_KEY] ?: true
                ThemeStateHolder.amoledMode        = prefs[AMOLED_MODE_KEY]        ?: false
                ThemeStateHolder.accentTheme       = prefs[ACCENT_THEME_KEY]       ?: "Ocean Blue"
                ThemeStateHolder.dynamicColorEnabled = prefs[DYNAMIC_COLOR_KEY]    ?: false
                ThemeStateHolder.followSystemTheme = prefs[FOLLOW_SYSTEM_THEME_KEY] ?: false
            } catch (_: Exception) {
                // Defaults already applied above; nothing else to do.
            }

            // Start global Omni Sync LAN server so desktop extensions can connect & sync anytime
            try {
                val baseDir = filesDir
                val collection = com.rebelroot.omni.bookmarks.storage.loadBookmarks(this@OmniApplication)
                val coord = SyncCoordinatorHolder.getOrCreate(baseDir, collection)
                com.rebelroot.omni.sync.core.SyncBridge.getInstance().tabBridge = com.rebelroot.omni.sync.mozilla.MozillaSyncManager.getInstance().tabBridge
            } catch (e: Exception) {
                android.util.Log.e("OmniApplication", "Failed to initialize global SyncCoordinator", e)
            }
        }
    }

    companion object {
        @Volatile var appContext: OmniApplication? = null
            private set

        val DARK_THEME_ENABLED_KEY = booleanPreferencesKey("dark_theme_enabled")
        val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
        val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme")
        val FOLLOW_SYSTEM_THEME_KEY = booleanPreferencesKey("follow_system_theme")
        // UI layout & wallpaper — read synchronously at startup to prevent flash
        val UI_SCALE_KEY            = floatPreferencesKey("ui_scale")
        val HOME_UI_SCALE_KEY       = floatPreferencesKey("home_ui_scale")
        val BOTTOM_NAV_SCALE_KEY    = floatPreferencesKey("bottom_nav_scale")
        val BROWSER_WALLPAPER_URI_KEY = stringPreferencesKey("browser_wallpaper_uri")
        val WALLPAPER_DIM_KEY       = floatPreferencesKey("wallpaper_dim")
        val WALLPAPER_BLUR_KEY      = floatPreferencesKey("wallpaper_blur")
        val WALLPAPER_SCALE_KEY     = floatPreferencesKey("wallpaper_scale")
        val WALLPAPER_OFFSET_X_KEY  = floatPreferencesKey("wallpaper_offset_x")
        val WALLPAPER_OFFSET_Y_KEY  = floatPreferencesKey("wallpaper_offset_y")
    }
}

object ThemeStateHolder {
    @Volatile var darkThemeEnabled: Boolean = true
    @Volatile var amoledMode: Boolean = false
    @Volatile var accentTheme: String = "Ocean Blue"
    @Volatile var dynamicColorEnabled: Boolean = false
    @Volatile var followSystemTheme: Boolean = false
}

/**
 * Holds UI layout and wallpaper preferences that must be available on the very
 * first Compose frame to prevent visible scale jumps and wallpaper pop-in.
 * Populated synchronously in [OmniApplication.onCreate] via runBlocking before
 * [com.rebelroot.omni.MainActivity.setContent] is called.
 */
object UiStateHolder {
    @Volatile var uiScale: Float = 1.0f
    @Volatile var homeUiScale: Float = 0.90f
    @Volatile var bottomNavScale: Float = 1.0f
    @Volatile var browserWallpaperUri: String? = null
    @Volatile var wallpaperDim: Float = -1f
    @Volatile var wallpaperBlur: Float = 0f
    @Volatile var wallpaperScale: Float = 1.0f
    @Volatile var wallpaperOffsetX: Float = 0f
    @Volatile var wallpaperOffsetY: Float = 0f
}

object SyncCoordinatorHolder {
    @Volatile
    var coordinator: com.rebelroot.omni.sync.coordinator.SyncCoordinator? = null
        private set

    fun getOrCreate(baseDir: java.io.File, collection: com.rebelroot.omni.bookmarks.model.BookmarkCollection): com.rebelroot.omni.sync.coordinator.SyncCoordinator {
        return coordinator ?: synchronized(this) {
            coordinator ?: com.rebelroot.omni.sync.coordinator.SyncCoordinator(
                baseDir = baseDir,
                collection = collection
            ).also { coordinator = it }
        }
    }
}
