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

package com.rebelroot.omni.media

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Detects and classifies media streams requested by the browser's web content.
 *
 * Architecture
 * ------------
 * Detection is intentionally SILENT and decoupled from any UI:
 *
 *   GeckoView / network interception
 *        -> onMediaRequestDetected / onAggressiveMediaGrabbed
 *        -> detectedMedia (raw, unfiltered-by-UI state)
 *        -> playableMedia (deduped, ranked, validated, current-page only)
 *        -> hasPlayableMedia (UI visibility state)
 *
 * The UI (address-bar media button + bottom sheet) observes [hasPlayableMedia]
 * and [playableMedia] ONLY. Detection continues regardless of whether the sheet
 * is open. Nothing is ever shown automatically.
 */
class MediaInterceptor {

    enum class MediaType { MP4, WEBM, HLS, DASH, AUDIO }

    /**
     * Best-effort classification of content protection. This is intentionally a
     * *status* rather than a hard boolean, because a URL containing "drm"/"widevine"
     * is only a weak signal and does not prove the native player cannot play it.
     */
    enum class MediaProtectionStatus {
        UNKNOWN,
        UNPROTECTED,
        LIKELY_PROTECTED,
        UNSUPPORTED
    }

    /**
     * Result of (optional) asynchronous playability validation.
     *  - PENDING  : validation not yet run (e.g. just detected, or disabled)
     *  - VALID    : endpoint responded and looks playable for ExoPlayer
     *  - INVALID  : endpoint clearly cannot be played (404/403/empty manifest)
     *  - UNKNOWN  : validation could not determine (network error, no context, etc.)
     *               Treated as "still show it" — a failed HEAD/GET is NOT proof of
     *               unusability.
     */
    enum class ValidationStatus { PENDING, VALID, INVALID, UNKNOWN }

    data class DetectedMedia(
        val url: String,
        val type: MediaType,
        val quality: String? = null,
        /** @deprecated retained for backward compatibility; prefer [protectionStatus]. */
        val isDrmProtected: Boolean = false,
        val protectionStatus: MediaProtectionStatus = MediaProtectionStatus.UNKNOWN,
        val sizeBytes: Long? = null,
        val cookies: String? = null,
        /** Referer used to detect/play the stream, preserved for native playback. */
        val referrer: String? = null,
        /** Origin used for the request, preserved for native playback. */
        val origin: String? = null,
        /** Full request headers captured at detection, preserved for native playback. */
        val headers: Map<String, String> = emptyMap(),
        /** Human-readable title for the source (page title or filename). */
        val title: String? = null,
        val validationStatus: ValidationStatus = ValidationStatus.PENDING,
        /** Identifier of the browser page/session this media belongs to. */
        val pageId: String = ""
    ) {
        /**
         * Build the explicit, user-initiated playback request for this stream,
         * carrying its full request context (auth, referer, origin, type).
         */
        fun toPlaybackRequest(): MediaPlaybackRequest {
            return MediaPlaybackRequest(
                url = url,
                mimeType = when (type) {
                    MediaType.MP4 -> "video/mp4"
                    MediaType.WEBM -> "video/webm"
                    MediaType.HLS -> "application/x-mpegURL"
                    MediaType.DASH -> "application/dash+xml"
                    MediaType.AUDIO -> "audio/*"
                },
                title = title,
                referrer = referrer,
                origin = origin,
                cookies = cookies,
                headers = headers,
                mediaType = type
            )
        }
    }

    /**
     * Explicit, user-initiated playback request passed from detection UI to the
     * native player. Carries the full request context so native playback reproduces
     * the exact stream the user selected (auth, referer, origin, type).
     *
     * Credentials (cookies/headers) are kept in-memory only — never persisted and
     * never serialized into navigation route arguments.
     */
    data class MediaPlaybackRequest(
        val url: String,
        val mimeType: String? = null,
        val title: String? = null,
        val referrer: String? = null,
        val origin: String? = null,
        val cookies: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val mediaType: MediaType
    )

    // ------------------------------------------------------------------
    // State flows
    // ------------------------------------------------------------------

    /** Long-lived scope for async detection/validation work. Declared before the
     *  derived state flows that depend on it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Raw detected media (silent background detection). Never drives UI directly. */
    private val _detectedMedia = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMedia>> = _detectedMedia.asStateFlow()

    /** Identifier of the page/session that is currently "in view". */
    private val _activePageId = MutableStateFlow("")

    /**
     * Media that is safe to show the user: tied to the active page, deduplicated,
     * ranked, and excluding definitively-invalid or unsupported streams.
     */
    val playableMedia: StateFlow<List<DetectedMedia>> =
        combine(_detectedMedia, _activePageId) { list, pageId ->
            computePlayable(list, pageId)
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** True when there is at least one playable source for the current page. */
    val hasPlayableMedia: StateFlow<Boolean> =
        playableMedia.map { it.isNotEmpty() }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    // ------------------------------------------------------------------
    // Configuration (set by BrowserViewModel from persistent settings)
    // ------------------------------------------------------------------

    /** When false, media detected on YouTube / Google domains is ignored (ToS-safe). */
    var isYouTubeEnabled = false

    /** Minimum video/audio duration in seconds extracted from URL params (0 = off). */
    var minDurationSeconds: Int = 0

    /** Domains on which Media Sniffer is completely disabled. */
    var blockedDomains: Set<String> = emptySet()

    /** Master toggle for background media detection. */
    var isMediaDetectionEnabled = true

    /** When true, streams are asynchronously validated before being shown as playable. */
    var isMediaValidationEnabled = true

    /** When true, the media button in the address bar is shown (subject to playable media). */
    var isMediaButtonEnabled = true

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Validation cache keyed by canonical URL; short-lived per session. */
    private val validationCache = mutableMapOf<String, ValidationStatus>()

    /** URLs currently being validated (to avoid duplicate in-flight jobs). */
    private val inFlightValidation = mutableSetOf<String>()

    /** Volatile query params that do not change the underlying media asset. */
    private val VOLATILE_PARAMS = setOf(
        "ctier", "ad_type", "oad", "bvt", "xtags", "rbuf", "rn", "sqp", "alr", "cpn"
    )

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Marks the active page/session. Media detected afterwards is tagged with this id,
     * and only media matching the active page is exposed via [playableMedia]. This gives
     * deterministic per-page invalidation (no stale media from a previous page).
     */
    fun setActivePage(pageId: String) {
        _activePageId.value = pageId
    }

    fun isDomainBlocked(url: String): Boolean {
        if (blockedDomains.isEmpty()) return false
        val host = try {
            android.net.Uri.parse(url).host?.lowercase()
        } catch (_: Exception) { null } ?: return false

        return blockedDomains.any { blocked ->
            val clean = blocked.trim().lowercase()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("www.")
                .trimEnd('/')
            if (clean.isEmpty()) return@any false
            host == clean || host.endsWith(".${clean}")
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("googlevideo.com")
    }

    private fun isTrackingOrStaticResource(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("ping.gif") ||
               lower.endsWith(".gif") || lower.contains(".gif?") ||
               lower.endsWith(".png") || lower.contains(".png?") ||
               lower.endsWith(".jpg") || lower.contains(".jpg?") ||
               lower.endsWith(".jpeg") || lower.contains(".jpeg?") ||
               lower.endsWith(".svg") || lower.contains(".svg?") ||
               lower.endsWith(".webp") || lower.contains(".webp?") ||
               lower.contains("analytics") || lower.contains("telemetry") ||
               lower.contains("pixel") || lower.contains("/ping")
    }

    private fun isAdVideo(url: String): Boolean {
        val lower = url.lowercase()

        val adPatterns = listOf(
            "doubleclick.net", "googleadservices", "pagead", "/ads/", "/ad/",
            "preroll", "midroll", "postroll", "adserver", "ad_stream",
            "adsystem", "adnxs", "rubiconproject", "openx.net", "spotxchange",
            "springserve", "brightcove.net/ads", "freewheel", "liverail",
            "yieldmo", "smartadserver", "contextweb"
        )
        if (adPatterns.any { lower.contains(it) }) return true

        if (lower.contains("googlevideo.com") || lower.contains("youtube.com")) {
            if (lower.contains("ad_type=")) return true
            if (lower.contains("ctier=a")) return true
            if (lower.contains("&oad=") || lower.contains("?oad=")) return true
        }

        try {
            val uri = android.net.Uri.parse(url)
            val durStr = uri.getQueryParameter("dur")
                ?: uri.getQueryParameter("duration")
                ?: uri.getQueryParameter("t")
            if (durStr != null) {
                val dur = durStr.toDoubleOrNull()
                if (dur != null && dur < 10.0) return true
            }
        } catch (_: Exception) { }

        return false
    }

    private fun getDurationSecondsFromUrl(url: String): Double? {
        return try {
            val uri = android.net.Uri.parse(url)
            val durStr = uri.getQueryParameter("dur")
                ?: uri.getQueryParameter("duration")
                ?: uri.getQueryParameter("dur_raw")
                ?: uri.getQueryParameter("d")
            durStr?.toDoubleOrNull()
        } catch (_: Exception) { null }
    }

    /** Called when the network interceptor detects a media asset request. */
    fun onMediaRequestDetected(url: String, headers: Map<String, String>? = null) {
        if (!isMediaDetectionEnabled) return
        if (isDomainBlocked(url)) return
        if (isTrackingOrStaticResource(url)) return
        if (isAdVideo(url)) {
            Log.i("MediaInterceptor", "Skipped ad video: $url")
            return
        }
        if (minDurationSeconds > 0) {
            val dur = getDurationSecondsFromUrl(url)
            if (dur != null && dur < minDurationSeconds) {
                Log.i("MediaInterceptor", "Skipped short media (${dur}s < min ${minDurationSeconds}s): $url")
                return
            }
        }

        val sizeBytes = headers?.get("Content-Length")?.toLongOrNull()
            ?: headers?.get("content-length")?.toLongOrNull()

        val type = classifyUrl(url) ?: return
        val cookies = headers?.get("Cookie") ?: headers?.get("cookie")
        val referrer = headers?.get("Referer") ?: headers?.get("referer")
        val origin = headers?.get("Origin") ?: headers?.get("origin")

        val protection = classifyProtection(url)
        val initialValidation = if (isMediaValidationEnabled) {
            validationCache[canonicalKey(url)] ?: ValidationStatus.PENDING
        } else ValidationStatus.VALID

        if (type == MediaType.HLS) {
            fetchAndParseHlsQualities(
                url, protection, cookies, referrer, origin,
                headers ?: emptyMap(), initialValidation
            )
        } else {
            val media = DetectedMedia(
                url = url,
                type = type,
                quality = extractQuality(url) ?: "Source HD",
                isDrmProtected = protection == MediaProtectionStatus.LIKELY_PROTECTED,
                protectionStatus = protection,
                sizeBytes = sizeBytes,
                cookies = cookies,
                referrer = referrer,
                origin = origin,
                headers = headers ?: emptyMap(),
                validationStatus = initialValidation,
                pageId = _activePageId.value
            )
            addMedia(media)
            Log.i("MediaInterceptor", "Intercepted Media: ${media.type} | protection: ${media.protectionStatus} | url: $url")
            maybeValidate(media)
        }
    }

    /** Aggressive capturing callback for MSE (Media Source Extensions) or Blob links. */
    fun onAggressiveMediaGrabbed(url: String, mimeType: String, cookies: String? = null) {
        if (!isMediaDetectionEnabled) return
        if (isDomainBlocked(url)) return
        if (isTrackingOrStaticResource(url)) return
        if (isAdVideo(url)) {
            Log.i("MediaInterceptor", "Skipped ad video (aggressive): $url")
            return
        }
        val type = when {
            mimeType.contains("video/mp4") -> MediaType.MP4
            mimeType.contains("video/webm") -> MediaType.WEBM
            mimeType.contains("application/x-mpegURL") || mimeType.contains("mpegurl") -> MediaType.HLS
            mimeType.contains("dash+xml") -> MediaType.DASH
            mimeType.contains("audio/") -> MediaType.AUDIO
            else -> MediaType.MP4
        }

        val protection = classifyProtection(url)
        val initialValidation = if (isMediaValidationEnabled) {
            validationCache[canonicalKey(url)] ?: ValidationStatus.PENDING
        } else ValidationStatus.VALID

        if (type == MediaType.HLS) {
            fetchAndParseHlsQualities(
                url, protection, cookies, null, null,
                emptyMap(), initialValidation
            )
        } else {
            val media = DetectedMedia(
                url = url,
                type = type,
                quality = "Source HD",
                isDrmProtected = false,
                protectionStatus = MediaProtectionStatus.UNPROTECTED,
                cookies = cookies,
                validationStatus = initialValidation,
                pageId = _activePageId.value
            )
            addMedia(media)
            Log.i("MediaInterceptor", "Aggressively captured media: ${media.type} | url: $url")
            maybeValidate(media)
        }
    }

    /**
     * Resets ALL detected media. Called on navigation/tab switch. The caller should
     * follow this with [setActivePage] for the new page so fresh detection starts clean.
     */
    fun clear() {
        _detectedMedia.value = emptyList()
        validationCache.clear()
        synchronized(inFlightValidation) { inFlightValidation.clear() }
    }

    // ------------------------------------------------------------------
    // HLS manifest fetch + parse
    // ------------------------------------------------------------------

    private fun fetchAndParseHlsQualities(
        urlStr: String,
        protection: MediaProtectionStatus,
        cookies: String?,
        referrer: String?,
        origin: String?,
        headers: Map<String, String>,
        initialValidation: ValidationStatus
    ) {
        scope.launch {
            try {
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                if (!cookies.isNullOrEmpty()) {
                    connection.setRequestProperty("Cookie", cookies)
                }
                if (!referrer.isNullOrEmpty()) {
                    connection.setRequestProperty("Referer", referrer)
                }
                if (!origin.isNullOrEmpty()) {
                    connection.setRequestProperty("Origin", origin)
                }
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                connection.connect()
                val manifestContent = connection.inputStream.bufferedReader().use { it.readText() }

                val parsedVariants = parseM3U8MasterPlaylist(urlStr, manifestContent)

                val pageId = _activePageId.value
                if (parsedVariants.isNotEmpty()) {
                    parsedVariants.forEach { variant ->
                        addMedia(
                            DetectedMedia(
                                url = variant.first,
                                type = MediaType.HLS,
                                quality = variant.second,
                                isDrmProtected = protection == MediaProtectionStatus.LIKELY_PROTECTED,
                                protectionStatus = protection,
                                cookies = cookies,
                                referrer = referrer,
                                origin = origin,
                                headers = headers,
                                // A successfully fetched + parsed master playlist is a
                                // strong signal the manifest is valid HLS.
                                validationStatus = ValidationStatus.VALID,
                                pageId = pageId
                            )
                        )
                    }
                } else {
                    addMedia(
                        DetectedMedia(
                            url = urlStr,
                            type = MediaType.HLS,
                            quality = "Auto / Source",
                            isDrmProtected = protection == MediaProtectionStatus.LIKELY_PROTECTED,
                            protectionStatus = protection,
                            cookies = cookies,
                            referrer = referrer,
                            origin = origin,
                            headers = headers,
                            validationStatus = ValidationStatus.VALID,
                            pageId = pageId
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("MediaInterceptor", "Failed to fetch/parse HLS manifest", e)
                // A fetch failure is NOT proof the stream is unusable (auth, transient
                // errors). Keep it visible with UNKNOWN so we don't hide playable media.
                addMedia(
                    DetectedMedia(
                        url = urlStr,
                        type = MediaType.HLS,
                        quality = "Auto / Source",
                        isDrmProtected = protection == MediaProtectionStatus.LIKELY_PROTECTED,
                        protectionStatus = protection,
                        cookies = cookies,
                        referrer = referrer,
                        origin = origin,
                        headers = headers,
                        validationStatus = ValidationStatus.UNKNOWN,
                        pageId = _activePageId.value
                    )
                )
            }
        }
    }

    private fun parseM3U8MasterPlaylist(baseUrl: String, content: String): List<Pair<String, String>> {
        val variants = mutableListOf<Pair<String, String>>()
        val lines = content.lines()
        var currentQuality = ""

        val baseUri = baseUrl.substring(0, baseUrl.lastIndexOf("/") + 1)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(trimmed)
                if (resMatch != null) {
                    val res = resMatch.groupValues[1]
                    val height = res.substringAfter("x").toIntOrNull() ?: 0
                    currentQuality = "${height}p"
                } else {
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(trimmed)
                    if (bwMatch != null) {
                        val kbps = (bwMatch.groupValues[1].toIntOrNull() ?: 0) / 1000
                        currentQuality = "${kbps}kbps"
                    } else {
                        currentQuality = "Unknown Quality"
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && currentQuality.isNotEmpty()) {
                val fullUrl = if (trimmed.startsWith("http")) trimmed else "$baseUri$trimmed"
                variants.add(fullUrl to currentQuality)
                currentQuality = ""
            }
        }

        return variants.distinctBy { it.second }.sortedByDescending {
            it.second.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Asynchronously validates a detected stream when validation is enabled and not
     * already cached. Validation preserves request context (cookies/referer/origin/UA)
     * so a protected stream is judged correctly. A failed validation is treated as
     * UNKNOWN (not INVALID) unless the endpoint clearly rejects the asset.
     */
    private fun maybeValidate(media: DetectedMedia) {
        if (!isMediaValidationEnabled) return
        if (media.validationStatus != ValidationStatus.PENDING) return
        val key = canonicalKey(media.url)
        synchronized(inFlightValidation) {
            if (inFlightValidation.contains(key)) return
            inFlightValidation.add(key)
        }
        scope.launch {
            try {
                val result = validateDirectMedia(media)
                validationCache[key] = result
                // Apply the result to the matching item(s) in the current list.
                _detectedMedia.update { list ->
                    list.map {
                        if (canonicalKey(it.url) == key && it.type == media.type) {
                            it.copy(validationStatus = result)
                        } else it
                    }
                }
            } finally {
                synchronized(inFlightValidation) { inFlightValidation.remove(key) }
            }
        }
    }

    /**
     * Lightweight playability probe for direct (non-HLS) media. Uses a ranged GET so we
     * do not download the whole file, and inspects status + content-type. This is a weak
     * signal only: a non-2xx response from a host that requires auth is reported as
     * UNKNOWN rather than INVALID so we never hide potentially-playable streams.
     */
    private suspend fun validateDirectMedia(media: DetectedMedia): ValidationStatus =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(media.url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=0-1")
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                media.cookies?.let { connection.setRequestProperty("Cookie", it) }
                media.referrer?.let { connection.setRequestProperty("Referer", it) }
                media.origin?.let { connection.setRequestProperty("Origin", it) }
                media.headers.forEach { (k, v) ->
                    if (k.equals("Cookie", true) || k.equals("Referer", true) || k.equals("Origin", true)) return@forEach
                    connection.setRequestProperty(k, v)
                }
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val code = connection.responseCode
                val contentType = connection.contentType?.lowercase() ?: ""

                when {
                    code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL -> {
                        if (contentType.contains("video/") || contentType.contains("audio/") ||
                            contentType.contains("application/octet-stream") || contentType.isEmpty()
                        ) ValidationStatus.VALID
                        else ValidationStatus.UNKNOWN
                    }
                    code == HttpURLConnection.HTTP_NOT_FOUND ||
                        code == 403 || code == 410 -> ValidationStatus.INVALID
                    else -> ValidationStatus.UNKNOWN
                }
            } catch (_: Exception) {
                ValidationStatus.UNKNOWN
            }
        }

    // ------------------------------------------------------------------
    // Playable list computation (dedupe + ranking)
    // ------------------------------------------------------------------

    private fun computePlayable(list: List<DetectedMedia>, pageId: String): List<DetectedMedia> {
        // 1. Only the active page's media.
        val pageMedia = if (pageId.isEmpty()) list else list.filter { it.pageId == pageId || it.pageId.isEmpty() }

        // 2. Exclude definitively unusable streams.
        val eligible = pageMedia.filter {
            it.validationStatus != ValidationStatus.INVALID &&
            it.protectionStatus != MediaProtectionStatus.UNSUPPORTED
        }

        // 3. Deduplicate by canonical identity.
        val seen = mutableSetOf<String>()
        val deduped = eligible.filter { media ->
            val key = canonicalKey(media.url) + "|" + media.type.name
            if (seen.contains(key)) false else { seen.add(key); true }
        }

        // 4. Rank deterministically.
        return deduped.sortedWith(PLAYABLE_COMPARATOR)
    }

    private val PLAYABLE_COMPARATOR = compareBy<DetectedMedia>(
        // Video before audio-only.
        { it.type == MediaType.AUDIO },
        // Non-protected before protected.
        { it.protectionStatus == MediaProtectionStatus.LIKELY_PROTECTED },
        // Higher quality number preferred.
        { -qualityRank(it.quality) },
        // Shorter, cleaner URLs (main content) before obvious auxiliary resources.
        { isAuxiliaryUrl(it.url) }
    ).thenBy { it.url }

    private fun qualityRank(quality: String?): Int {
        if (quality == null) return 0
        val h = Regex("(\\d{3,4})p").find(quality)?.groupValues?.get(1)?.toIntOrNull()
        if (h != null) return h
        val kbps = Regex("(\\d+)kbps").find(quality)?.groupValues?.get(1)?.toIntOrNull()
        return kbps ?: 0
    }

    /** Heuristic: URLs containing obvious thumbnail/auxiliary markers rank lower. */
    private fun isAuxiliaryUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("thumb") || lower.contains("poster") ||
               lower.contains("preview") || lower.contains("sprite") ||
               lower.contains("still") || lower.contains("snapshot")
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun addMedia(media: DetectedMedia) {
        _detectedMedia.update { current ->
            // Raw-URL + type dedupe so repeated identical detections don't accumulate.
            if (current.any { it.url == media.url && it.type == media.type }) current
            else current + media
        }
    }

    private fun classifyUrl(url: String): MediaType? {
        val lower = url.lowercase()

        val mimeFromQuery = try {
            android.net.Uri.parse(url).getQueryParameter("mime")?.let {
                java.net.URLDecoder.decode(it, "UTF-8").lowercase()
            }
        } catch (e: Exception) { null }

        if (mimeFromQuery != null) {
            return when {
                mimeFromQuery.contains("video/mp4") -> MediaType.MP4
                mimeFromQuery.contains("video/webm") -> MediaType.WEBM
                mimeFromQuery.contains("application/x-mpegurl") || mimeFromQuery.contains("mpegurl") -> MediaType.HLS
                mimeFromQuery.contains("dash+xml") -> MediaType.DASH
                mimeFromQuery.contains("audio/") -> MediaType.AUDIO
                else -> null
            }
        }

        return when {
            lower.endsWith(".m3u8") || lower.contains(".m3u8") || lower.contains("m3u8") -> MediaType.HLS
            lower.endsWith(".mpd") || lower.contains(".mpd") || lower.contains("/dash/") -> MediaType.DASH
            lower.endsWith(".mp4") || lower.contains(".mp4") -> MediaType.MP4
            lower.endsWith(".webm") || lower.contains(".webm") -> MediaType.WEBM
            lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".m4a") -> MediaType.AUDIO
            else -> null
        }
    }

    private fun classifyProtection(url: String): MediaProtectionStatus {
        val lower = url.lowercase()
        // Weak signal only — URL markers are not proof of protection.
        return if (lower.contains("drm") || lower.contains("widevine") || lower.contains("playready") ||
            lower.contains("license") || lower.contains("clearkey")
        ) MediaProtectionStatus.LIKELY_PROTECTED
        else MediaProtectionStatus.UNPROTECTED
    }

    private fun extractQuality(url: String): String? {
        val regex = Regex("(\\d{3,4})p")
        return regex.find(url)?.groupValues?.get(1)?.let { "${it}p" }
    }

    /**
     * Canonical identity for deduplication: scheme + host + path + a stable subset of
     * query params (volatile tracking params removed). Different qualities/itags are
     * intentionally preserved as distinct streams.
     */
    private fun canonicalKey(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: "http"
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path ?: ""
            val kept = uri.queryParameterNames
                .filterNot { it.lowercase() in VOLATILE_PARAMS }
                .sorted()
                .joinToString("&") { "$it=${uri.getQueryParameter(it)}" }
            "$scheme://$host$path?$kept"
        } catch (_: Exception) { url }
    }
}
