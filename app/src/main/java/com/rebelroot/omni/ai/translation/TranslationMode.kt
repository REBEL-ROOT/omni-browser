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

package com.rebelroot.omni.ai.translation

/**
 * Policy controlling which translation backend may be used.
 *
 * - [OFFLINE_ONLY]  : Only the local engine may run. If no offline model exists
 *                     for the pair, translation FAILS — it must never silently
 *                     fall back to a cloud service.
 * - [ONLINE_ONLY]   : Only the remote service may run.
 * - [ASK]           : The UI should prompt the user. When no prompt is possible
 *                     (e.g. programmatic call) it prefers offline when a model
 *                     exists, otherwise online.
 */
enum class TranslationMode {
    OFFLINE_ONLY,
    ONLINE_ONLY,
    ASK;

    companion object {
        fun fromPreference(value: String?): TranslationMode = when (value) {
            "offline_only" -> OFFLINE_ONLY
            "online_only" -> ONLINE_ONLY
            else -> ASK
        }
    }
}
