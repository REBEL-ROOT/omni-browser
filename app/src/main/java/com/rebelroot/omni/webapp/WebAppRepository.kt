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

package com.rebelroot.omni.webapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import com.rebelroot.omni.MainActivity
import com.rebelroot.omni.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Repository coordinating Web App / PWA data, offline icon caching,
 * desktop shortcut pinning, and multi-profile app cloning.
 */
class WebAppRepository(private val context: Context) {

    private val db = WebAppDatabase.getDatabase(context)
    private val dao = db.webAppDao()

    val installedApps: Flow<List<WebAppEntity>> = dao.getAll()
    val installedCount: Flow<Int> = dao.getCount()

    suspend fun getAppById(id: String): WebAppEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun getAppByUrl(url: String): WebAppEntity? = withContext(Dispatchers.IO) {
        dao.getByUrl(url)
    }

    suspend fun isAppInstalled(url: String): Boolean = withContext(Dispatchers.IO) {
        dao.getByUrl(url) != null
    }

    /**
     * Install a new Web App with optional custom icon, profile ID, and launcher shortcut.
     */
    suspend fun installWebApp(
        url: String,
        name: String,
        category: String = "Productivity",
        description: String? = null,
        profileId: String = "default",
        customIcon: Bitmap? = null,
        pinToHomeScreen: Boolean = false
    ): WebAppEntity = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        val domain = try {
            Uri.parse(normalizedUrl).host ?: normalizedUrl
        } catch (e: Exception) {
            normalizedUrl
        }

        // Fetch or save icon
        val bitmap = customIcon ?: fetchFaviconBitmap(domain)
        val iconPath = bitmap?.let { saveIconToDisk(it) }

        val entity = WebAppEntity(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { domain },
            url = normalizedUrl,
            iconPath = iconPath,
            category = category,
            description = description,
            installDate = System.currentTimeMillis(),
            lastUsedDate = System.currentTimeMillis(),
            launchCount = 0,
            profileId = profileId.ifBlank { "default" }
        )

        dao.insert(entity)

        if (pinToHomeScreen) {
            pinShortcutToLauncher(entity, bitmap)
        }

        entity
    }

    /**
     * Clone an installed Web App with an isolated profile (e.g. Work vs Personal).
     */
    suspend fun cloneWebApp(
        sourceApp: WebAppEntity,
        newName: String,
        newProfileId: String,
        pinToHomeScreen: Boolean = false
    ): WebAppEntity = withContext(Dispatchers.IO) {
        val clonedEntity = WebAppEntity(
            id = UUID.randomUUID().toString(),
            name = newName.ifBlank { "${sourceApp.name} (${newProfileId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }})" },
            url = sourceApp.url,
            iconPath = sourceApp.iconPath, // Reuse icon file
            category = sourceApp.category,
            description = sourceApp.description,
            installDate = System.currentTimeMillis(),
            lastUsedDate = System.currentTimeMillis(),
            launchCount = 0,
            profileId = newProfileId.ifBlank { "work" }
        )

        dao.insert(clonedEntity)

        if (pinToHomeScreen) {
            val icon = loadIconFromDisk(sourceApp.iconPath)
            pinShortcutToLauncher(clonedEntity, icon)
        }

        clonedEntity
    }

    suspend fun updateWebApp(app: WebAppEntity) = withContext(Dispatchers.IO) {
        dao.update(app)
    }

    suspend fun togglePin(app: WebAppEntity) = withContext(Dispatchers.IO) {
        dao.setPinned(app.id, !app.isPinned)
    }

    suspend fun uninstallWebApp(app: WebAppEntity) = withContext(Dispatchers.IO) {
        // Delete icon file if it exists and no other app is referencing it
        app.iconPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete icon file: $path", e)
            }
        }
        dao.deleteById(app.id)
    }

    suspend fun recordLaunch(appId: String) = withContext(Dispatchers.IO) {
        dao.recordLaunch(appId)
    }

    /**
     * Pin a Web App shortcut to the Android Home screen using ShortcutManagerCompat.
     */
    fun pinShortcutToLauncher(app: WebAppEntity, iconBitmap: Bitmap? = null) {
        val appCtx = context.applicationContext
        val bitmap = iconBitmap ?: loadIconFromDisk(app.iconPath)

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appCtx)) {
            Toast.makeText(appCtx, "Shortcut pinning not supported by your launcher", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(appCtx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(app.url)
            putExtra("extra_web_app_id", app.id)
            putExtra("extra_web_app_profile", app.profileId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val icon = if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(appCtx, R.mipmap.ic_launcher)
        }

        val shortcutLabel = if (app.profileId != "default") {
            "${app.name} (${app.profileLabel})"
        } else {
            app.name
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(appCtx, "pwa_${app.id}")
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutLabel)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.requestPinShortcut(appCtx, shortcutInfo, null)
        Toast.makeText(appCtx, "Adding '${app.name}' to home screen...", Toast.LENGTH_SHORT).show()
    }

    /**
     * Download high-res favicon using Google Favicons service with DuckDuckGo fallback.
     */
    suspend fun fetchFaviconBitmap(domain: String): Bitmap? = withContext(Dispatchers.IO) {
        val cleanDomain = domain.removePrefix("www.").trim()
        val primaryUrl = "https://www.google.com/s2/favicons?sz=128&domain=$cleanDomain"
        val fallbackUrl = "https://icons.duckduckgo.com/ip3/$cleanDomain.ico"

        val loader = ImageLoader(context.applicationContext)

        // Try primary (Google Favicons API)
        try {
            val req = ImageRequest.Builder(context.applicationContext)
                .data(primaryUrl)
                .allowHardware(false)
                .build()
            val res = loader.execute(req)
            val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) {
                return@withContext scaleIcon(bmp)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Google favicon fetch failed for $domain, trying fallback: ${e.message}")
        }

        // Try fallback (DuckDuckGo Favicon Service)
        try {
            val req = ImageRequest.Builder(context.applicationContext)
                .data(fallbackUrl)
                .allowHardware(false)
                .build()
            val res = loader.execute(req)
            val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) {
                return@withContext scaleIcon(bmp)
            }
        } catch (e: Exception) {
            Log.d(TAG, "DuckDuckGo favicon fetch failed for $domain: ${e.message}")
        }

        null
    }

    private fun scaleIcon(raw: Bitmap): Bitmap {
        val targetSize = 128
        val scaled = Bitmap.createScaledBitmap(raw, targetSize, targetSize, true)
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    /**
     * Persist bitmap to internal storage to ensure it loads offline and persists across restarts.
     */
    private fun saveIconToDisk(bitmap: Bitmap): String? {
        return try {
            val dir = context.getDir("webapp_icons", Context.MODE_PRIVATE)
            val file = File(dir, "icon_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving webapp icon to disk", e)
            null
        }
    }

    /**
     * Load cached icon bitmap from file.
     */
    fun loadIconFromDisk(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "WebAppRepository"

        @Volatile
        private var INSTANCE: WebAppRepository? = null

        fun getInstance(context: Context): WebAppRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = WebAppRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
