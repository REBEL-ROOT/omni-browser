/*
 * Omni Browser - Backup & Restore models
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Defines the user-selectable sections that can be included in a backup file.
 */

package com.rebelroot.omni.backup

/**
 * A category of data that the user can choose to export/import.
 *
 * @param key stable identifier stored in the backup file
 * @param defaultEnabled whether the checkbox starts ticked in the export UI
 * @param sensitive true for data that is stored unencrypted and should be
 *        opt-in only (currently passwords)
 */
enum class BackupSection(val key: String, val defaultEnabled: Boolean, val sensitive: Boolean) {
    SETTINGS("settings", defaultEnabled = true, sensitive = false),
    BOOKMARKS("bookmarks", defaultEnabled = true, sensitive = false),
    HISTORY("history", defaultEnabled = true, sensitive = false),
    PASSWORDS("passwords", defaultEnabled = false, sensitive = true),
    NOTES("notes", defaultEnabled = true, sensitive = false);

    companion object {
        fun fromKey(key: String): BackupSection? = entries.firstOrNull { it.key == key }
    }
}

/** Result of inspecting a backup file before importing it. */
data class BackupInspection(
    val schemaVersion: Int,
    /** Sections actually present in the file. */
    val available: List<BackupSection>,
    /** Approximate item count per section, for the import preview. */
    val counts: Map<BackupSection, Int>
) {
    fun countFor(section: BackupSection): Int = counts[section] ?: 0
}