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

package com.rebelroot.omni.tools.locker

import android.content.Context

class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("omni_locker_prefs", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean {
        return prefs.getString("in_app_pin", null) != null
    }

    fun setPin(pin: String) {
        prefs.edit().putString("in_app_pin", pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        return prefs.getString("in_app_pin", null) == pin
    }
}
