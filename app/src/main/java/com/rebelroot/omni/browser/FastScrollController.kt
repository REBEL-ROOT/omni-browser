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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import java.util.Locale
import kotlin.math.abs

/**
 * Controlled, low-latency, coalescing scroll dispatcher for the fast-scroll pill.
 *
 * Instead of firing unthrottled loadUri calls on every raw pointer move (which creates
 * severe IPC backlog and stutter), this controller maintains an active in-flight worker
 * backed by a [Channel.CONFLATED] queue.
 *
 * Pointer events (60–120 Hz) update the conflated channel instantly. The worker loop
 * dispatches the latest scroll position at display frame intervals (~16 ms) without backlog.
 *
 * The DOM script targets `document.scrollingElement || document.documentElement || document.body`
 * and main app containers (SPA roots, article containers) to reliably scroll every site type.
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
     * Attaches to a [GeckoSession] and ensures the background consumer loop is running.
     */
    fun attachSession(session: GeckoSession?) {
        currentSession = session
        ensureWorkerRunning()
    }

    private fun ensureWorkerRunning() {
        if (workerJob?.isActive == true) return

        workerJob = scope.launch {
            var lastDispatchedFrac = -1f
            for (request in scrollChannel) {
                if (!isActive) break
                val targetSession = currentSession ?: continue
                if (!targetSession.isOpen) {
                    continue
                }

                val fraction = request.fraction
                if (abs(fraction - lastDispatchedFrac) < 0.0005f && lastDispatchedFrac >= 0f) {
                    continue
                }
                lastDispatchedFrac = fraction

                try {
                    val fractionStr = String.format(Locale.US, "%.5f", fraction)
                    val script = "javascript:(function(f){try{var doc=document,win=window,se=doc.scrollingElement||doc.documentElement||doc.body,docH=Math.max(doc.documentElement?doc.documentElement.scrollHeight:0,doc.body?doc.body.scrollHeight:0,se?se.scrollHeight:0),viewH=win.innerHeight||(doc.documentElement?doc.documentElement.clientHeight:0),max=Math.max(0,docH-viewH);if(max>0){var targetY=Math.round(f*max);if(f<=0.015)targetY=0;if(f>=0.985)targetY=max;win.scrollTo({top:targetY,left:0,behavior:'instant'});if(se&&se.scrollTop!==targetY)se.scrollTop=targetY;if(doc.documentElement&&doc.documentElement.scrollTop!==targetY)doc.documentElement.scrollTop=targetY;if(doc.body&&doc.body.scrollTop!==targetY)doc.body.scrollTop=targetY;}else{var cands=doc.querySelectorAll('main,#root,#app,[role=\"main\"],[class*=\"scroll\" i],[class*=\"feed\" i]');for(var i=0;i<cands.length;i++){var c=cands[i];if(c&&c.scrollHeight>c.clientHeight&&c.clientHeight>viewH*0.3){var cMax=c.scrollHeight-c.clientHeight;var cTargetY=Math.round(f*cMax);if(f<=0.015)cTargetY=0;if(f>=0.985)cTargetY=cMax;c.scrollTop=cTargetY;break;}}}}catch(e){}})($fractionStr);"
                    targetSession.loadUri(script)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to dispatch scroll: ${e.message}")
                }

                // Throttle to display frame interval (~16ms) to avoid queueing in Gecko
                delay(16L)
            }
        }
    }

    /**
     * Enqueues a new target scroll fraction (0.0f .. 1.0f).
     * Older unconsumed requests are automatically dropped by the conflated channel.
     */
    fun dispatchDragFraction(fraction: Float, maxDocumentScroll: Float) {
        ensureWorkerRunning()
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
