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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rebelroot.omni.browser.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            runBlocking {
                val prefs = dataStore.data.first()
                ThemeStateHolder.darkThemeEnabled = prefs[DARK_THEME_ENABLED_KEY] ?: true
                ThemeStateHolder.amoledMode = prefs[AMOLED_MODE_KEY] ?: false
                ThemeStateHolder.accentTheme = prefs[ACCENT_THEME_KEY] ?: "Ocean Blue"
                ThemeStateHolder.dynamicColorEnabled = prefs[DYNAMIC_COLOR_KEY] ?: false
            }
        } catch (e: Exception) {
            ThemeStateHolder.darkThemeEnabled = true
            ThemeStateHolder.amoledMode = false
            ThemeStateHolder.accentTheme = "Ocean Blue"
            ThemeStateHolder.dynamicColorEnabled = false
        }
    }

    companion object {
        val DARK_THEME_ENABLED_KEY = booleanPreferencesKey("dark_theme_enabled")
        val AMOLED_MODE_KEY = booleanPreferencesKey("amoled_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
        val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme")
    }
}

object ThemeStateHolder {
    var darkThemeEnabled: Boolean = true
    var amoledMode: Boolean = false
    var accentTheme: String = "Ocean Blue"
    var dynamicColorEnabled: Boolean = false
}
