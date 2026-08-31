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

    val mangaPipeline: com.rebelroot.omni.ai.manga.MangaTranslationPipeline by lazy {
        com.rebelroot.omni.ai.manga.MangaTranslationPipeline(coordinator)
    }

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            if (nativeApp != "omniTranslate") return null
            val msg = message as? JSONObject ?: return null
            val type = msg.optString("type")

            val req = request ?: return GeckoResult.fromValue("{}")
            val result = GeckoResult<Any>()

            if (type == "translate") {
                val segments = parseSegments(msg.optJSONArray("segments") ?: return GeckoResult.fromValue("{}"))
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
            } else if (type == "translateImage") {
                val imageId = msg.optString("imageId", "img_0")
                val base64Data = msg.optString("base64")
                val srcLang = msg.optString("sourceLang").takeIf { it.isNotEmpty() } ?: req.sourceLanguage ?: "ja"
                val tgtLang = msg.optString("targetLang").takeIf { it.isNotEmpty() } ?: req.targetLanguage

                scope.launch {
                    try {
                        if (base64Data.isNotEmpty()) {
                            val cleanBase64 = base64Data.substringAfter("base64,")
                            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                val transRes = mangaPipeline.translateImage(imageId, bitmap, srcLang, tgtLang)
                                val stream = java.io.ByteArrayOutputStream()
                                transRes.translatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
                                val outData = "data:image/jpeg;base64," + android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)

                                val resp = org.json.JSONObject()
                                resp.put("imageId", imageId)
                                resp.put("translatedSrc", outData)
                                result.complete(resp.toString())
                                return@launch
                            }
                        }
                        result.complete("{}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Image translation failed for $imageId", e)
                        result.complete("{}")
                    }
                }
                return result
            }

            return null
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
