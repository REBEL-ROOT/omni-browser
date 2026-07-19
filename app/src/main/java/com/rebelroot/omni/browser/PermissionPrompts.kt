package com.rebelroot.omni.browser

import org.mozilla.geckoview.GeckoSession

data class ContentPermissionPrompt(
    val siteUri: String,
    val permissionType: Int,
    val onAllow: () -> Unit,
    val onDeny: () -> Unit
)

data class SystemPermissionRequest(
    val permissions: Array<String>?,
    val onGranted: () -> Unit,
    val onDenied: () -> Unit
)

data class MediaPermissionPrompt(
    val siteUri: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val onAllow: (videoSource: GeckoSession.PermissionDelegate.MediaSource?, audioSource: GeckoSession.PermissionDelegate.MediaSource?) -> Unit,
    val onDeny: () -> Unit
)
