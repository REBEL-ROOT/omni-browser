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

package com.rebelroot.omni.browser

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength

/**
 * Controlled, low-latency, coalescing scroll dispatcher for the fast-scroll pill.
 *
 * Instead of routing per-frame pointer events through GeckoSession.loadUri("javascript:..."),
 * this controller dispatches scroll requests directly to GeckoView's native APZ
 * (Async Pan/Zoom) [PanZoomController] pipeline using [PanZoomController.scrollTo].
 *
 * High-frequency pointer events (60–120 Hz) are coalesced via a [Channel.CONFLATED] queue,
 * guaranteeing that:
 * 1. Only the newest requested position is executed.
 * 2. Stale intermediate frames are dropped without backlog.
 * 3. Gecko receives at most one scroll update per frame.
 */
class FastScrollController(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FastScrollController"
    }

    private data class ScrollRequest(
        val fraction: Float,
        val maxDocumentScroll: Float
    )

    private val scrollChannel = Channel<ScrollRequest>(Channel.CONFLATED)
    private var workerJob: Job? = null
    private var currentSession: GeckoSession? = null

    /**
     * Attaches to a [GeckoSession] and starts the coalesced consumption loop.
     */
    fun attachSession(session: GeckoSession?) {
        if (currentSession === session && workerJob?.isActive == true) return

        workerJob?.cancel()
        currentSession = session

        if (session != null && session.isOpen) {
            workerJob = scope.launch {
                for (request in scrollChannel) {
                    if (!isActive) break
                    val targetSession = currentSession ?: break
                    if (!targetSession.isOpen) break

                    try {
                        val fraction = request.fraction
                        val maxScroll = request.maxDocumentScroll

                        if (fraction <= 0f) {
                            targetSession.panZoomController.scrollToTop()
                        } else if (fraction >= 1f) {
                            targetSession.panZoomController.scrollToBottom()
                        } else {
                            val targetPx = FastScrollMath.computeDocumentScrollTarget(fraction, maxScroll)
                            targetSession.panZoomController.scrollTo(
                                ScreenLength.zero(),
                                ScreenLength.fromPixels(targetPx.toDouble()),
                                PanZoomController.SCROLL_BEHAVIOR_AUTO
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to dispatch APZ scroll: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Enqueues a new target scroll fraction (0.0f .. 1.0f).
     * Older unconsumed requests are automatically dropped by the conflated channel.
     */
    fun dispatchDragFraction(fraction: Float, maxDocumentScroll: Float) {
        scrollChannel.trySend(ScrollRequest(fraction.coerceIn(0f, 1f), maxOf(0f, maxDocumentScroll)))
    }

    /**
     * Immediately dispatches a scroll to the top of the document.
     */
    fun scrollToTop() {
        dispatchDragFraction(0f, 0f)
    }

    /**
     * Immediately dispatches a scroll to the bottom of the document.
     */
    fun scrollToBottom(maxDocumentScroll: Float) {
        dispatchDragFraction(1f, maxDocumentScroll)
    }

    /**
     * Detaches and cancels active jobs.
     */
    fun detach() {
        workerJob?.cancel()
        workerJob = null
        currentSession = null
    }
}
