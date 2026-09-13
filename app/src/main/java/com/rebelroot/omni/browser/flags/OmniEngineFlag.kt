/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.rebelroot.omni.browser.flags

/**
 * Indicates which browser inspired this flag implementation.
 */
enum class FlagOrigin(val displayName: String) {
    CHROME("Chrome Flag"),
    BRAVE("Brave Shields"),
    FIREFOX("Firefox Core")
}

/**
 * UI grouping categories for flags in omni:config.
 */
enum class FlagCategory(val displayName: String) {
    ALL("All"),
    BRAVE_SHIELDS("Brave Shields"),
    PERFORMANCE("Chrome Performance"),
    SECURITY("Security & Protocol"),
    MEDIA_UI("Media & Display"),
    NETWORK("Network & DNS"),
    CUSTOM("Custom")
}

/**
 * Represents a curated browser flag mapped to underlying GeckoView/Firefox engine preferences.
 *
 * @param id Unique identifier stored in persistent preferences
 * @param title User-facing title of the flag
 * @param description Detailed explanation of what the flag does and its engine impact
 * @param origin Source platform inspiration (Chrome, Brave, or Firefox)
 * @param tag Flag identifier tag (e.g. "#enable-parallel-downloading")
 * @param category Category under which this flag appears in omni:config
 * @param enginePrefs Native Firefox preferences applied when this flag is ENABLED
 * @param defaultEnabled Default state of the flag
 * @param requiresRestart Whether changing this flag requires an engine restart
 */
data class OmniEngineFlag(
    val id: String,
    val title: String,
    val description: String,
    val origin: FlagOrigin,
    val tag: String,
    val category: FlagCategory,
    val enginePrefs: Map<String, Any>,
    val defaultEnabled: Boolean = false,
    val requiresRestart: Boolean = true
)

/**
 * Data type for user-added custom preferences (about:config mode).
 */
enum class PrefType(val displayName: String) {
    BOOLEAN("Boolean"),
    INT("Integer"),
    STRING("String")
}

/**
 * Arbitrary custom preference entered by a power user.
 */
data class CustomPref(
    val key: String,
    val type: PrefType,
    val value: String
)
