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

package com.rebelroot.omni.ai.models

/** The AI task a model serves. Used to route models to the correct engine. */
enum class ModelTask(val key: String) {
    TRANSLATION("translation"),
    ASR("asr"),
    /** Reserved for future local models (e.g. summarisation). */
    OTHER("other");

    companion object {
        fun fromKey(key: String?): ModelTask = when (key) {
            "translation" -> TRANSLATION
            "asr" -> ASR
            else -> OTHER
        }
    }
}
