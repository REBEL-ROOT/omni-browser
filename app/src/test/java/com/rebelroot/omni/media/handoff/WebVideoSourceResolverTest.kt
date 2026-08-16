/*
 * Omni Browser — Web Video Source Resolver Tests
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.media.handoff

import com.rebelroot.omni.media.MediaInterceptor
import org.junit.Assert.*
import org.junit.Test

class WebVideoSourceResolverTest {

    @Test
    fun `test direct MP4 source resolution`() {
        val session = WebVideoSession(
            sessionId = "sess_1",
            tabId = "tab_1",
            videoElementId = "vid_1",
            sourceUri = "https://example.com/video.mp4",
            pageUrl = "https://example.com/watch",
            mimeType = "video/mp4"
        )

        val result = WebVideoSourceResolver.resolve(session)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Success)
        val success = result as WebVideoSourceResolver.ResolutionResult.Success
        assertEquals("https://example.com/video.mp4", success.resolvedUri)
        assertEquals("video/mp4", success.mimeType)
        assertEquals(MediaSourceType.DIRECT_MP4, success.sourceType)
    }

    @Test
    fun `test direct HLS manifest resolution`() {
        val session = WebVideoSession(
            sessionId = "sess_2",
            tabId = "tab_1",
            videoElementId = "vid_2",
            sourceUri = "https://example.com/stream/master.m3u8",
            pageUrl = "https://example.com/watch"
        )

        val result = WebVideoSourceResolver.resolve(session)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Success)
        val success = result as WebVideoSourceResolver.ResolutionResult.Success
        assertEquals("https://example.com/stream/master.m3u8", success.resolvedUri)
        assertEquals(MediaSourceType.HLS, success.sourceType)
    }

    @Test
    fun `test blob source resolution using video-associated streams`() {
        val session = WebVideoSession(
            sessionId = "sess_3",
            tabId = "tab_1",
            videoElementId = "vid_mse",
            sourceUri = "blob:https://example.com/d9b4-3a21-9988",
            pageUrl = "https://example.com/anime"
        )

        val associated = listOf(
            "https://cdn.example.com/ep1/segment-1.ts",
            "https://cdn.example.com/ep1/master.m3u8"
        )

        val result = WebVideoSourceResolver.resolve(session, associatedStreams = associated)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Success)
        val success = result as WebVideoSourceResolver.ResolutionResult.Success
        assertEquals("https://cdn.example.com/ep1/master.m3u8", success.resolvedUri)
        assertEquals(MediaSourceType.HLS, success.sourceType)
    }

    @Test
    fun `test blob source resolution using tab-scoped detected media`() {
        val session = WebVideoSession(
            sessionId = "sess_4",
            tabId = "tab_omni_42",
            videoElementId = "vid_mse_2",
            sourceUri = "blob:https://streaming.to/5544-2211",
            pageUrl = "https://streaming.to/watch?v=123"
        )

        val tabMedia = listOf(
            MediaInterceptor.DetectedMedia(
                url = "https://stream.server.net/hls/playlist.m3u8",
                type = MediaInterceptor.MediaType.HLS,
                cookies = "auth=token123",
                pageId = "tab_omni_42",
                referrer = "https://streaming.to/watch?v=123"
            )
        )

        val result = WebVideoSourceResolver.resolve(session, tabDetectedMedia = tabMedia)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Success)
        val success = result as WebVideoSourceResolver.ResolutionResult.Success
        assertEquals("https://stream.server.net/hls/playlist.m3u8", success.resolvedUri)
        assertEquals(MediaSourceType.HLS, success.sourceType)
        assertEquals("auth=token123", success.cookies)
    }

    @Test
    fun `test blob source returns UnresolvedBlob when no streams exist`() {
        val session = WebVideoSession(
            sessionId = "sess_5",
            tabId = "tab_5",
            videoElementId = "vid_empty_blob",
            sourceUri = "blob:https://unresolved.site/abc",
            pageUrl = "https://unresolved.site"
        )

        val result = WebVideoSourceResolver.resolve(session, associatedStreams = emptyList(), tabDetectedMedia = emptyList())
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.UnresolvedBlob)
    }

    @Test
    fun `test isMediaSegment correctly filters out chunks`() {
        assertTrue(WebVideoSourceResolver.isMediaSegment("https://cdn.net/hls/segment_001.ts"))
        assertTrue(WebVideoSourceResolver.isMediaSegment("https://cdn.net/dash/chunk-1.m4s"))
        assertTrue(WebVideoSourceResolver.isMediaSegment("https://cdn.net/video/segment/12"))
        assertFalse(WebVideoSourceResolver.isMediaSegment("https://cdn.net/video/master.m3u8"))
        assertFalse(WebVideoSourceResolver.isMediaSegment("https://cdn.net/video/stream.mpd"))
        assertFalse(WebVideoSourceResolver.isMediaSegment("https://cdn.net/video/file.mp4"))
    }

    @Test
    fun `test googlevideo parameter preservation`() {
        val gvideoUrl = "https://rr1---sn-4g5edn6s.googlevideo.com/videoplayback?expire=1755432&ei=abc&ip=1.2.3.4&id=xyz&itag=22&source=youtube&requiressl=yes"
        val session = WebVideoSession(
            sessionId = "sess_6",
            tabId = "tab_6",
            videoElementId = "vid_youtube",
            sourceUri = gvideoUrl,
            pageUrl = "https://youtube.com/watch?v=xyz"
        )

        val result = WebVideoSourceResolver.resolve(session)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Success)
        val success = result as WebVideoSourceResolver.ResolutionResult.Success
        assertEquals(gvideoUrl, success.resolvedUri)
        assertTrue(success.resolvedUri.contains("expire=1755432"))
        assertTrue(success.resolvedUri.contains("itag=22"))
    }

    @Test
    fun `test unsupported media type returns Unsupported result`() {
        val session = WebVideoSession(
            sessionId = "sess_7",
            tabId = "tab_7",
            videoElementId = "vid_drm",
            sourceUri = "https://drm.protected.com/manifest.wvd",
            pageUrl = "https://drm.protected.com"
        )

        val result = WebVideoSourceResolver.resolve(session)
        assertTrue(result is WebVideoSourceResolver.ResolutionResult.Unsupported)
    }
}
