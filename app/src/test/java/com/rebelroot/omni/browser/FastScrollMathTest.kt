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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollMathTest {

    private val eps = 1e-4f

    @Test
    fun testComputeThumbHeight_boundsAndClamping() {
        val minThumb = 36f
        val maxThumb = 90f

        // Normal range within bounds
        val h1 = FastScrollMath.computeThumbHeight(trackHeight = 600f, thumbFraction = 0.1f, minThumbPx = minThumb, maxThumbPx = maxThumb)
        assertEquals(60f, h1, eps)

        // Below minimum -> clamped to minThumb
        val h2 = FastScrollMath.computeThumbHeight(trackHeight = 500f, thumbFraction = 0.02f, minThumbPx = minThumb, maxThumbPx = maxThumb)
        assertEquals(minThumb, h2, eps)

        // Above maximum -> clamped to maxThumb
        val h3 = FastScrollMath.computeThumbHeight(trackHeight = 1000f, thumbFraction = 0.5f, minThumbPx = minThumb, maxThumbPx = maxThumb)
        assertEquals(maxThumb, h3, eps)

        // Invalid inputs
        val hNan = FastScrollMath.computeThumbHeight(trackHeight = Float.NaN, thumbFraction = 0.1f, minThumbPx = minThumb, maxThumbPx = maxThumb)
        assertEquals(minThumb, hNan, eps)

        val hZero = FastScrollMath.computeThumbHeight(trackHeight = 0f, thumbFraction = 0.1f, minThumbPx = minThumb, maxThumbPx = maxThumb)
        assertEquals(minThumb, hZero, eps)
    }

    @Test
    fun testComputeMaxThumbTravel() {
        assertEquals(910f, FastScrollMath.computeMaxThumbTravel(trackHeight = 1000f, thumbHeight = 90f), eps)
        assertEquals(0f, FastScrollMath.computeMaxThumbTravel(trackHeight = 50f, thumbHeight = 60f), eps)
        assertEquals(0f, FastScrollMath.computeMaxThumbTravel(trackHeight = Float.NaN, thumbHeight = 50f), eps)
    }

    @Test
    fun testComputeDragFraction_fingerMapping() {
        val topOffset = 80f
        val thumbHeight = 60f
        val maxTravel = 500f

        // Finger at exact top: fingerY = topOffset + thumbHeight / 2 = 80 + 30 = 110
        val fracTop = FastScrollMath.computeDragFraction(
            fingerY = 110f,
            topTrackOffset = topOffset,
            thumbHeight = thumbHeight,
            maxThumbTravel = maxTravel
        )
        assertEquals(0.0f, fracTop, eps)

        // Finger at midpoint: fingerY = 110 + 250 = 360
        val fracMid = FastScrollMath.computeDragFraction(
            fingerY = 360f,
            topTrackOffset = topOffset,
            thumbHeight = thumbHeight,
            maxThumbTravel = maxTravel
        )
        assertEquals(0.5f, fracMid, eps)

        // Finger at bottom: fingerY = 110 + 500 = 610
        val fracBot = FastScrollMath.computeDragFraction(
            fingerY = 610f,
            topTrackOffset = topOffset,
            thumbHeight = thumbHeight,
            maxThumbTravel = maxTravel
        )
        assertEquals(1.0f, fracBot, eps)

        // Finger dragged above top (e.g. into status bar area) -> clamped to 0f
        val fracAbove = FastScrollMath.computeDragFraction(
            fingerY = 20f,
            topTrackOffset = topOffset,
            thumbHeight = thumbHeight,
            maxThumbTravel = maxTravel
        )
        assertEquals(0.0f, fracAbove, eps)

        // Finger dragged below bottom (e.g. into navigation bar area) -> clamped to 1f
        val fracBelow = FastScrollMath.computeDragFraction(
            fingerY = 900f,
            topTrackOffset = topOffset,
            thumbHeight = thumbHeight,
            maxThumbTravel = maxTravel
        )
        assertEquals(1.0f, fracBelow, eps)
    }

    @Test
    fun testComputeDocumentScrollTarget_proportions() {
        val maxScroll = 4000f

        assertEquals(0f, FastScrollMath.computeDocumentScrollTarget(0.0f, maxScroll), eps)
        assertEquals(2000f, FastScrollMath.computeDocumentScrollTarget(0.5f, maxScroll), eps)
        assertEquals(4000f, FastScrollMath.computeDocumentScrollTarget(1.0f, maxScroll), eps)
        assertEquals(1000f, FastScrollMath.computeDocumentScrollTarget(0.25f, maxScroll), eps)

        // Out of bounds fractions
        assertEquals(0f, FastScrollMath.computeDocumentScrollTarget(-0.5f, maxScroll), eps)
        assertEquals(4000f, FastScrollMath.computeDocumentScrollTarget(1.5f, maxScroll), eps)

        // Zero / negative maxScroll
        assertEquals(0f, FastScrollMath.computeDocumentScrollTarget(0.5f, 0f), eps)
        assertEquals(0f, FastScrollMath.computeDocumentScrollTarget(0.5f, -100f), eps)
        assertEquals(0f, FastScrollMath.computeDocumentScrollTarget(0.5f, Float.NaN), eps)
    }

    @Test
    fun testComputeMaxDocumentScroll() {
        // DOM metrics valid
        val maxDom = FastScrollMath.computeMaxDocumentScroll(
            pageScrollHeight = 5000f,
            pageViewportHeight = 1000f,
            scrollRange = 0,
            scrollExtent = 0
        )
        assertEquals(4000f, maxDom, eps)

        // DOM metrics absent / zero -> GeckoView fallback
        val maxGecko = FastScrollMath.computeMaxDocumentScroll(
            pageScrollHeight = 0f,
            pageViewportHeight = 0f,
            scrollRange = 6000,
            scrollExtent = 1500
        )
        assertEquals(4500f, maxGecko, eps)

        // Non-scrollable page (scrollHeight <= viewportHeight)
        val maxShort = FastScrollMath.computeMaxDocumentScroll(
            pageScrollHeight = 800f,
            pageViewportHeight = 1000f,
            scrollRange = 1000,
            scrollExtent = 1000
        )
        assertEquals(0f, maxShort, eps)

        // NaN metrics
        val maxNan = FastScrollMath.computeMaxDocumentScroll(
            pageScrollHeight = Float.NaN,
            pageViewportHeight = 1000f,
            scrollRange = 0,
            scrollExtent = 0
        )
        assertEquals(0f, maxNan, eps)
    }

    @Test
    fun testComputeThumbFraction() {
        // Normal 10-screen page -> 1000 / 10000 = 0.10f
        val tf1 = FastScrollMath.computeThumbFraction(
            pageScrollHeight = 10000f,
            pageViewportHeight = 1000f,
            scrollRange = 0,
            scrollExtent = 0
        )
        assertEquals(0.10f, tf1, eps)

        // Short page -> 1000 / 2000 = 0.50f -> clamped to max 0.25f
        val tf2 = FastScrollMath.computeThumbFraction(
            pageScrollHeight = 2000f,
            pageViewportHeight = 1000f,
            scrollRange = 0,
            scrollExtent = 0
        )
        assertEquals(0.25f, tf2, eps)

        // Huge 1000-screen page -> 1000 / 1000000 = 0.001f -> clamped to min 0.06f
        val tf3 = FastScrollMath.computeThumbFraction(
            pageScrollHeight = 1_000_000f,
            pageViewportHeight = 1000f,
            scrollRange = 0,
            scrollExtent = 0
        )
        assertEquals(0.06f, tf3, eps)

        // Fallback to GeckoView
        val tfGecko = FastScrollMath.computeThumbFraction(
            pageScrollHeight = 0f,
            pageViewportHeight = 0f,
            scrollRange = 10000,
            scrollExtent = 1500
        )
        assertEquals(0.15f, tfGecko, eps)
    }

    @Test
    fun testComputeScrollFraction() {
        assertEquals(0f, FastScrollMath.computeScrollFraction(0f, 4000f), eps)
        assertEquals(0.25f, FastScrollMath.computeScrollFraction(1000f, 4000f), eps)
        assertEquals(0.50f, FastScrollMath.computeScrollFraction(2000f, 4000f), eps)
        assertEquals(1.0f, FastScrollMath.computeScrollFraction(4000f, 4000f), eps)
        assertEquals(1.0f, FastScrollMath.computeScrollFraction(5000f, 4000f), eps)
        assertEquals(0f, FastScrollMath.computeScrollFraction(100f, 0f), eps)
        assertEquals(0f, FastScrollMath.computeScrollFraction(Float.NaN, 4000f), eps)
    }

    @Test
    fun testExtremelyLargeDocuments() {
        // Document of 10,000,000 pixels (10,000 screens)
        val pageSH = 10_000_000f
        val pageVH = 1000f
        val maxScroll = FastScrollMath.computeMaxDocumentScroll(pageSH, pageVH, 0, 0)
        assertEquals(9_999_000f, maxScroll, eps)

        val targetHalf = FastScrollMath.computeDocumentScrollTarget(0.5f, maxScroll)
        assertEquals(4_999_500f, targetHalf, eps)

        val targetEnd = FastScrollMath.computeDocumentScrollTarget(1.0f, maxScroll)
        assertEquals(9_999_000f, targetEnd, eps)

        val fracEnd = FastScrollMath.computeScrollFraction(targetEnd, maxScroll)
        assertEquals(1.0f, fracEnd, eps)
    }

    @Test
    fun testDynamicHeightChangesDuringDrag() {
        // User starts dragging at 50% on a 4000px maxScroll document
        val initialMaxScroll = 4000f
        val initialFingerY = 360f // 50%
        val topOffset = 80f
        val thumbHeight = 60f
        val maxTravel = 500f

        val initialFrac = FastScrollMath.computeDragFraction(initialFingerY, topOffset, thumbHeight, maxTravel)
        assertEquals(0.5f, initialFrac, eps)

        val initialTarget = FastScrollMath.computeDocumentScrollTarget(initialFrac, initialMaxScroll)
        assertEquals(2000f, initialTarget, eps)

        // Page dynamically loads more content -> maxScroll becomes 8000px
        val updatedMaxScroll = 8000f
        // Drag fraction from finger position remains 0.5f
        val updatedFrac = FastScrollMath.computeDragFraction(initialFingerY, topOffset, thumbHeight, maxTravel)
        assertEquals(0.5f, updatedFrac, eps)

        // New target becomes 4000px smoothly
        val updatedTarget = FastScrollMath.computeDocumentScrollTarget(updatedFrac, updatedMaxScroll)
        assertEquals(4000f, updatedTarget, eps)
        assertTrue(updatedTarget <= updatedMaxScroll)
    }
}
