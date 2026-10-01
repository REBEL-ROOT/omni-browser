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

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing an installed Progressive Web App (PWA) or registered Web Application.
 *
 * Features pro-level multi-profile management (inspired by Nira Browser), usage telemetry,
 * pin-to-top flags, and local cached icon paths.
 */
@Entity(tableName = "web_apps")
data class WebAppEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val iconPath: String? = null,
    val category: String = "Productivity",
    val description: String? = null,
    val installDate: Long = System.currentTimeMillis(),
    val lastUsedDate: Long = System.currentTimeMillis(),
    val launchCount: Int = 0,
    val profileId: String = "default",
    val isPinned: Boolean = false,
    val displayMode: String = "standalone"
) {
    /**
     * Clean domain for display (e.g. "notion.so")
     */
    val displayDomain: String
        get() = try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.removePrefix("www.") else host
        } catch (e: Exception) {
            url
        }

    /**
     * Friendly profile label
     */
    val profileLabel: String
        get() = when (profileId.lowercase()) {
            "default" -> "Default"
            "work" -> "Work"
            "personal" -> "Personal"
            "school" -> "School"
            else -> profileId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
