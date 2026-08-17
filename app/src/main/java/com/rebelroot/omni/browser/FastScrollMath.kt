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

/**
 * State machine for the Safari/iOS-style fast-scroll pill gesture.
 */
enum class ScrollDragState {
    IDLE,
    TOUCH_DOWN,
    DRAGGING,
    RELEASED,
    CANCELLED
}

/**
 * Pure mathematical functions for scrollbar geometry, coordinate mapping,
 * and document scroll synchronization.
 */
object FastScrollMath {

    /**
     * Computes the visible thumb height based on the track height and thumb fraction,
     * clamped between [minThumbPx] and [maxThumbPx].
     */
    fun computeThumbHeight(
        trackHeight: Float,
        thumbFraction: Float,
        minThumbPx: Float,
        maxThumbPx: Float
    ): Float {
        if (trackHeight.isNaN() || trackHeight.isInfinite() || trackHeight <= 0f) return minThumbPx
        val validThumbFrac = if (thumbFraction.isNaN() || thumbFraction.isInfinite()) 0.15f else thumbFraction.coerceIn(0.01f, 1f)
        val rawHeight = trackHeight * validThumbFrac
        val minH = if (minThumbPx.isNaN() || minThumbPx <= 0f) 36f else minThumbPx
        val maxH = if (maxThumbPx.isNaN() || maxThumbPx < minH) maxOf(minH, trackHeight) else maxThumbPx
        return rawHeight.coerceIn(minH, maxH)
    }

    /**
     * Computes the maximum vertical travel distance for the thumb within the track.
     */
    fun computeMaxThumbTravel(trackHeight: Float, thumbHeight: Float): Float {
        if (trackHeight.isNaN() || thumbHeight.isNaN()) return 0f
        return maxOf(0f, trackHeight - thumbHeight)
    }

    /**
     * Converts a raw touch Y coordinate (within the touch strip container) into a normalized
     * scroll fraction in the range 0.0f .. 1.0f.
     *
     * The finger coordinate is centered on the thumb:
     * targetThumbY = fingerY - topTrackOffset - (thumbHeight / 2)
     */
    fun computeDragFraction(
        fingerY: Float,
        topTrackOffset: Float,
        thumbHeight: Float,
        maxThumbTravel: Float
    ): Float {
        if (fingerY.isNaN() || topTrackOffset.isNaN() || thumbHeight.isNaN() || maxThumbTravel.isNaN()) return 0f
        if (maxThumbTravel <= 0f) return 0f

        val targetThumbY = fingerY - topTrackOffset - (thumbHeight / 2f)
        val clampedThumbY = targetThumbY.coerceIn(0f, maxThumbTravel)
        return (clampedThumbY / maxThumbTravel).coerceIn(0f, 1f)
    }

    /**
     * Maps a scroll fraction (0.0f .. 1.0f) to the target document vertical scroll offset in pixels.
     */
    fun computeDocumentScrollTarget(fraction: Float, maxDocumentScroll: Float): Float {
        if (fraction.isNaN() || maxDocumentScroll.isNaN() || maxDocumentScroll <= 0f) return 0f
        val clampedFrac = fraction.coerceIn(0f, 1f)
        return (clampedFrac * maxDocumentScroll).coerceIn(0f, maxDocumentScroll)
    }

    /**
     * Computes the maximum scrollable document distance using DOM metrics first,
     * falling back to GeckoView scroll range/extent.
     */
    fun computeMaxDocumentScroll(
        pageScrollHeight: Float,
        pageViewportHeight: Float,
        scrollRange: Int,
        scrollExtent: Int
    ): Float {
        if (!pageScrollHeight.isNaN() && !pageViewportHeight.isNaN() &&
            pageScrollHeight > 0f && pageViewportHeight > 0f && pageScrollHeight > pageViewportHeight
        ) {
            return pageScrollHeight - pageViewportHeight
        }
        val rangeF = scrollRange.toFloat()
        val extentF = scrollExtent.toFloat()
        if (rangeF > 0f && extentF > 0f && rangeF > extentF) {
            return rangeF - extentF
        }
        return 0f
    }

    /**
     * Computes the thumb fraction (ratio of viewport to total scrollable height)
     * clamped to a sensible range (e.g. 0.06f .. 0.25f).
     */
    fun computeThumbFraction(
        pageScrollHeight: Float,
        pageViewportHeight: Float,
        scrollRange: Int,
        scrollExtent: Int,
        minFrac: Float = 0.06f,
        maxFrac: Float = 0.25f
    ): Float {
        if (!pageScrollHeight.isNaN() && !pageViewportHeight.isNaN() &&
            pageScrollHeight > 0f && pageViewportHeight > 0f && pageScrollHeight >= pageViewportHeight
        ) {
            return (pageViewportHeight / pageScrollHeight).coerceIn(minFrac, maxFrac)
        }
        val rangeF = scrollRange.toFloat()
        val extentF = scrollExtent.toFloat()
        if (rangeF > 0f && extentF > 0f && rangeF >= extentF) {
            return (extentF / rangeF).coerceIn(minFrac, maxFrac)
        }
        return minFrac
    }

    /**
     * Computes the current scroll fraction from current physical/DOM scroll offset.
     */
    fun computeScrollFraction(currentScrollOffset: Float, maxDocumentScroll: Float): Float {
        if (currentScrollOffset.isNaN() || maxDocumentScroll.isNaN() || maxDocumentScroll <= 0f) return 0f
        return (currentScrollOffset / maxDocumentScroll).coerceIn(0f, 1f)
    }
}
