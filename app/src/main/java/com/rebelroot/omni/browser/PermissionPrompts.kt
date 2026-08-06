package com.rebelroot.omni.browser

import org.mozilla.geckoview.GeckoSession

/**
 * Wraps a GeckoView content-permission request (location, notifications, DRM, storage).
 * Three outcomes:
 *   onAllow       — grant & remember for this site
 *   onAllowOnce   — grant for this session only (not persisted)
 *   onDeny        — deny & remember for this site
 */
data class ContentPermissionPrompt(
    val siteUri: String,
    val permissionType: Int,
    val onAllow: () -> Unit,
    val onAllowOnce: () -> Unit,
    val onDeny: () -> Unit
)

/**
 * Wraps an Android OS permission request (camera, mic, location system permissions).
 */
data class SystemPermissionRequest(
    val permissions: Array<String>?,
    val rationaleTitle: String,
    val rationaleBody: String,
    val onGranted: () -> Unit,
    val onDenied: () -> Unit
)

/**
 * Wraps a GeckoView WebRTC media request (camera/mic device selection).
 * Three outcomes:
 *   onAllow       — grant & remember for this site
 *   onAllowOnce   — grant for this session only (not persisted)
 *   onDeny        — deny & remember for this site
 */
data class MediaPermissionPrompt(
    val siteUri: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val videoSources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
    val audioSources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
    val onAllow: (
        videoSource: GeckoSession.PermissionDelegate.MediaSource?,
        audioSource: GeckoSession.PermissionDelegate.MediaSource?
    ) -> Unit,
    val onAllowOnce: (
        videoSource: GeckoSession.PermissionDelegate.MediaSource?,
        audioSource: GeckoSession.PermissionDelegate.MediaSource?
    ) -> Unit,
    val onDeny: () -> Unit
)
