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

package com.rebelroot.omni.ai.web

import android.util.Log
import com.rebelroot.omni.ai.models.Json
import com.rebelroot.omni.ai.models.JsonValue
import com.rebelroot.omni.ai.translation.TranslationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Bridges page text from the `omni-translate` content script to the app's
 * [TranslationCoordinator].
 *
 * The content script (running in the page) sends `{nativeApp:"omniTranslate",
 * type:"translate", segments:[{i,text}]}`. This delegate translates the segments
 * (deduplicated via [PageTranslationPlanner]) and returns a JSON map `{i: text}`
 * which the content script applies to the DOM.
 *
 * Privacy: only text leaves the page; no model paths, files, or download manager
 * are ever exposed to web content. The active [TranslationRequest] (including the
 * target language) is chosen by the app, never by the page.
 */
class OmniTranslateBridge(
    private val coordinator: TranslationCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    @Volatile var request: TranslationRequest? = null

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            if (nativeApp != "omniTranslate") return null
            val msg = message as? JSONObject ?: return null
            if (msg.optString("type") != "translate") return null

            val req = request ?: return GeckoResult.fromValue("{}")
            val segments = parseSegments(msg.optJSONArray("segments") ?: return GeckoResult.fromValue("{}"))

            val result = GeckoResult<Any>()
            scope.launch {
                try {
                    val out = PageTranslationPlanner.translateSegments(coordinator, segments, req.sourceLanguage, req.targetLanguage)
                    result.complete(buildMap(out.segments))
                } catch (e: Exception) {
                    Log.e(TAG, "Page translation failed", e)
                    result.complete("{}")
                }
            }
            return result
        }
    }

    /** Register this delegate on the loaded `omni-translate` extension. */
    fun register(extension: WebExtension) {
        extension.setMessageDelegate(delegate, "omniTranslate")
    }

    private fun parseSegments(arr: JSONArray): List<PageSegment> {
        val out = mutableListOf<PageSegment>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("text")
            if (text.isNullOrEmpty()) continue
            out.add(PageSegment(o.optString("i", i.toString()), text))
        }
        return out
    }

    private fun buildMap(segments: List<TranslatedSegment>): String {
        val obj = JsonValue.Obj(
            segments.associate { it.id to Json.str(it.text) }
        )
        return Json.write(obj)
    }

    companion object {
        private const val TAG = "OmniTranslateBridge"
    }
}
