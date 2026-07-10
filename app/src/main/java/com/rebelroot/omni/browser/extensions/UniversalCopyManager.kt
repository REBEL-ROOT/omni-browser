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

package com.rebelroot.omni.browser.extensions

import org.mozilla.geckoview.GeckoRuntime

/**
 * Manages the Universal Copy built-in extension.
 *
 * Many sites (Medium, Quora, news paywalls) set `user-select: none` on their
 * content or intercept clipboard events via JavaScript to prevent copying. This
 * extension injects a content script that removes those restrictions on page load,
 * restoring native text selection and clipboard behaviour for the user.
 *
 * Delegates all lifecycle management to [BuiltInExtensionManager].
 *
 * NOTE: This extension must be granted private-browsing access so it works on
 * paywalled sites visited in incognito mode. [BuiltInExtensionManager] handles
 * this via setAllowedInPrivateBrowsing(true) on install.
 *
 * FIXME: Some single-page apps re-apply the user-select restriction after route
 *        changes via their framework's lifecycle hooks. The content script currently
 *        only runs on initial page load — needs a MutationObserver to catch reruns.
 */
class UniversalCopyManager(runtime: GeckoRuntime) : BuiltInExtensionManager(
    runtime = runtime,
    assetPath = "web_extensions/universal_copy/",
    extensionId = "omni-universal-copy@omnibrowser.app",
    label = "Universal Copy"
)
