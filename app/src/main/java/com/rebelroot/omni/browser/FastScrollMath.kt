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
 * State machine for the Safari/iOS-style fast-scroll pill.
 */
enum class ScrollPillState {
    DISABLED,        // Disabled in settings, home screen, or unsupported page
    NON_SCROLLABLE,  // Page content height <= viewport height (no scroll needed)
    VISIBLE_IDLE,    // Actively visible (during scrolling or within 1500ms settle delay)
    FADED,           // Inactive on scrollable page (alpha = 0, ready to appear on scroll/touch)
    DRAGGING         // Currently being dragged by user finger (expanded, alpha = 1.0)
}

/**
 * Common, unified geometry representation for both visual pill rendering and local touch hit-testing.
 */
data class ScrollGeometry(
    val maxDocumentScroll: Float,
    val scrollFraction: Float,
    val thumbFraction: Float,
    val thumbHeight: Float,
    val maxThumbTravel: Float,
    val thumbY: Float,
    val isScrollable: Boolean,
    val hitboxTop: Float,
    val hitboxBottom: Float,
    val hitboxLeft: Float,
    val hitboxRight: Float
)

/**
 * Pure mathematical functions for scrollbar geometry, coordinate mapping,
 * document scroll synchronization, and local hitbox testing.
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
        val validThumbFrac = if (thumbFraction.isNaN() || thumbFraction.isInfinite()) 0.08f else thumbFraction.coerceIn(0.01f, 1f)
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
     * Converts a raw touch Y coordinate (within the viewport) into a normalized
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
        val rawFrac = targetThumbY / maxThumbTravel
        return when {
            rawFrac <= 0.015f -> 0f
            rawFrac >= 0.985f -> 1f
            else -> rawFrac.coerceIn(0f, 1f)
        }
    }

    /**
     * Maps a scroll fraction (0.0f .. 1.0f) to the target document vertical scroll offset in pixels.
     */
    fun computeDocumentScrollTarget(fraction: Float, maxDocumentScroll: Float): Float {
        if (fraction.isNaN() || maxDocumentScroll.isNaN() || maxDocumentScroll <= 0f) return 0f
        return when {
            fraction <= 0.015f -> 0f
            fraction >= 0.985f -> maxDocumentScroll
            else -> (fraction * maxDocumentScroll).coerceIn(0f, maxDocumentScroll)
        }
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
     * clamped to a sensible range (e.g. 0.04f .. 0.20f).
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
        return 0.08f
    }

    /**
     * Computes the current scroll fraction from current physical/DOM scroll offset.
     */
    fun computeScrollFraction(currentScrollOffset: Float, maxDocumentScroll: Float): Float {
        if (currentScrollOffset.isNaN() || maxDocumentScroll.isNaN() || maxDocumentScroll <= 0f) return 0f
        return (currentScrollOffset / maxDocumentScroll).coerceIn(0f, 1f)
    }

    /**
     * Authoritative single geometry calculator used by BOTH visual Canvas drawing
     * and touch hitbox hit-testing.
     */
    fun computeGeometry(
        viewportWidth: Float,
        viewportHeight: Float,
        topTrackOffset: Float,
        bottomTrackOffset: Float,
        pageScrollHeight: Float,
        pageViewportHeight: Float,
        scrollRange: Int,
        scrollExtent: Int,
        currentScrollOffset: Float,
        isDragging: Boolean,
        dragFraction: Float,
        minThumbPx: Float = 36f,
        maxThumbPx: Float = 90f,
        hitboxWidthPx: Float = 48f,
        hitboxTolerancePx: Float = 20f,
        minHitboxHeightPx: Float = 64f
    ): ScrollGeometry {
        val calculatedMaxScroll = computeMaxDocumentScroll(pageScrollHeight, pageViewportHeight, scrollRange, scrollExtent)
        val isExplicitlyShort = (!pageScrollHeight.isNaN() && !pageViewportHeight.isNaN() && pageScrollHeight > 0f && pageViewportHeight > 0f && pageScrollHeight <= pageViewportHeight) ||
                (scrollRange > 0 && scrollExtent > 0 && scrollRange <= scrollExtent)

        val isScrollable = if (isExplicitlyShort) false else (calculatedMaxScroll > 0f || isDragging || currentScrollOffset > 0f || (viewportHeight > 0f && pageScrollHeight == 0f && scrollRange == 0))
        val maxScroll = if (calculatedMaxScroll > 0f) calculatedMaxScroll else maxOf(currentScrollOffset * 1.5f, viewportHeight * 2f).coerceAtLeast(1000f)

        val safeTop = if (topTrackOffset.isNaN()) 0f else topTrackOffset.coerceAtLeast(0f)
        val safeBot = if (bottomTrackOffset.isNaN()) 0f else bottomTrackOffset.coerceAtLeast(0f)
        val safeVH = if (viewportHeight.isNaN()) 0f else viewportHeight.coerceAtLeast(0f)
        val safeVW = if (viewportWidth.isNaN()) 0f else viewportWidth.coerceAtLeast(0f)

        val trackH = (safeVH - safeTop - safeBot).coerceAtLeast(10f)
        val thumbFrac = computeThumbFraction(pageScrollHeight, pageViewportHeight, scrollRange, scrollExtent)
        val thumbH = computeThumbHeight(trackH, thumbFrac, minThumbPx, maxThumbPx)
        val maxTravel = computeMaxThumbTravel(trackH, thumbH)

        val displayedFrac = if (isDragging) {
            dragFraction.coerceIn(0f, 1f)
        } else {
            computeScrollFraction(currentScrollOffset, maxScroll)
        }

        val thumbY = safeTop + displayedFrac * maxTravel

        // Hitbox centered around the thumb with vertical tolerance and minimum touch target size
        val halfMinHitbox = minHitboxHeightPx / 2f
        val thumbCenterY = thumbY + thumbH / 2f
        val rawHitboxTop = minOf(thumbY - hitboxTolerancePx, thumbCenterY - halfMinHitbox)
        val rawHitboxBottom = maxOf(thumbY + thumbH + hitboxTolerancePx, thumbCenterY + halfMinHitbox)

        val hitboxTop = rawHitboxTop.coerceIn(safeTop, safeTop + trackH)
        val hitboxBottom = rawHitboxBottom.coerceIn(hitboxTop, safeTop + trackH)
        val hitboxLeft = (safeVW - hitboxWidthPx).coerceAtLeast(0f)
        val hitboxRight = safeVW

        return ScrollGeometry(
            maxDocumentScroll = maxScroll,
            scrollFraction = displayedFrac,
            thumbFraction = thumbFrac,
            thumbHeight = thumbH,
            maxThumbTravel = maxTravel,
            thumbY = thumbY,
            isScrollable = isScrollable,
            hitboxTop = hitboxTop,
            hitboxBottom = hitboxBottom,
            hitboxLeft = hitboxLeft,
            hitboxRight = hitboxRight
        )
    }

    /**
     * Checks if a touch event coordinate (touchX, touchY) is strictly within the local thumb hitbox.
     */
    fun isTouchInsideHitbox(
        touchX: Float,
        touchY: Float,
        geometry: ScrollGeometry
    ): Boolean {
        if (!geometry.isScrollable) return false
        val isInsideX = touchX >= geometry.hitboxLeft && touchX <= (geometry.hitboxRight + 12f)
        val isInsideY = touchY >= geometry.hitboxTop && touchY <= geometry.hitboxBottom
        return isInsideX && isInsideY
    }

    /**
     * Checks if a touch coordinate is within the right edge swipe strip.
     */
    fun isTouchNearRightEdge(
        touchX: Float,
        viewportWidth: Float,
        edgeStripWidthPx: Float = 48f
    ): Boolean {
        if (viewportWidth <= 0f) return false
        val leftBound = (viewportWidth - edgeStripWidthPx).coerceAtLeast(0f)
        return touchX >= leftBound && touchX <= (viewportWidth + 24f)
    }
}
