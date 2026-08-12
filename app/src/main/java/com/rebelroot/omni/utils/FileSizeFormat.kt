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

package com.rebelroot.omni.utils

/**
 * Shared 1024-based byte-size math used by the locker and download-manager
 * byte formatting (the duplicated Math.log/Math.pow logic).
 *
 * Rounding decimals and unit labels are intentionally NOT part of this helper —
 * callers keep their own presentation so existing output is preserved exactly.
 */
object FileSizeFormat {

    /** 1024-based unit-group index for [bytes] (0 = bytes, 1 = KB, 2 = MB, …). */
    fun unitIndex(bytes: Long): Int =
        if (bytes <= 0) 0 else (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

    /** [bytes] expressed in its 1024-based unit group. */
    fun inUnits(bytes: Long, groups: Int): Double =
        bytes / Math.pow(1024.0, groups.toDouble())
}