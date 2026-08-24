package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.model.FractionalIndex
import org.junit.Assert.*
import org.junit.Test

class FractionalIndexTest {

    @Test
    fun fromDensePosition_generatesAscendingKeys() {
        val keys = (0L..100L).map { FractionalIndex.fromDensePosition(it) }
        val sorted = keys.sorted()
        assertEquals("Dense keys must be naturally lexicographically sorted", sorted, keys)
        assertEquals("a0", keys[0])
        assertEquals("a1", keys[1])
    }

    @Test
    fun generateBetween_insertsStrictlyBetweenSiblings() {
        val k0 = "a0"
        val k1 = "a1"
        val mid = FractionalIndex.generateBetween(k0, k1)
        assertTrue("mid ($mid) must be > k0 ($k0)", mid > k0)
        assertTrue("mid ($mid) must be < k1 ($k1)", mid < k1)

        val mid2 = FractionalIndex.generateBetween(k0, mid)
        assertTrue(mid2 > k0)
        assertTrue(mid2 < mid)
    }

    @Test
    fun generateBetween_handlesNullBounds() {
        val first = FractionalIndex.generateBetween(null, null)
        assertEquals("a0", first)

        val beforeFirst = FractionalIndex.generateBetween(null, "a0")
        assertTrue(beforeFirst < "a0")

        val afterFirst = FractionalIndex.generateBetween("a0", null)
        assertTrue(afterFirst > "a0")
    }
}
