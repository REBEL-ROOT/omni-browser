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
 * Manages the AI Blocker built-in extension.
 *
 * Blocks AI-generated overview summaries, assistant panels, and sponsored AI
 * widgets injected by search engines (primarily Google Search and Bing).
 *
 * Delegates all lifecycle management to [BuiltInExtensionManager] — the shared
 * base class for all of Omni's bundled GeckoView extensions. See that class for
 * detailed documentation on the install/enable/private-browsing flow.
 *
 * TODO: Replace static pattern injection with a dynamic rule-update mechanism
 *       so new AI widget selectors can be pushed without a full app update.
 */
class AiBlockerManager(runtime: GeckoRuntime) : BuiltInExtensionManager(
    runtime = runtime,
    assetPath = "web_extensions/ai_blocker/",
    extensionId = "omni-ai-blocker@omnibrowser.app",
    label = "AI Blocker"
)
