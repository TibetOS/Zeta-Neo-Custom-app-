package com.traffko.outlanderhub.vehicle.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsMathTest {

    @Test
    fun `standstill creep snaps to zero`() {
        assertEquals(0f, clampCreepToZero(0f))
        assertEquals(0f, clampCreepToZero(1.9f))
    }

    @Test
    fun `real motion passes through unchanged`() {
        assertEquals(2f, clampCreepToZero(2f))
        assertEquals(87.4f, clampCreepToZero(87.4f))
    }

    @Test
    fun `fallback speed from position delta`() {
        // 25 m covered in 1 s = 90 km/h.
        assertEquals(90f, fallbackSpeedKmh(distanceM = 25f, dtMs = 1_000)!!, 0.01f)
    }

    @Test
    fun `fallback rejects implausible jumps and bad dt`() {
        // A 200 m teleport in 1 s (720 km/h) is a bad fix, not motion.
        assertNull(fallbackSpeedKmh(distanceM = 200f, dtMs = 1_000))
        assertNull(fallbackSpeedKmh(distanceM = 10f, dtMs = 0))
        assertNull(fallbackSpeedKmh(distanceM = 10f, dtMs = -5))
    }

    @Test
    fun `compass points at cardinal bearings`() {
        assertEquals("N", compassPoint(0f))
        assertEquals("NE", compassPoint(45f))
        assertEquals("E", compassPoint(90f))
        assertEquals("S", compassPoint(180f))
        assertEquals("W", compassPoint(270f))
        assertEquals("NW", compassPoint(315f))
    }

    @Test
    fun `compass sector boundaries`() {
        assertEquals("N", compassPoint(22.4f))
        assertEquals("NE", compassPoint(22.5f))
        assertEquals("N", compassPoint(337.5f))
    }

    @Test
    fun `compass normalizes out-of-range bearings`() {
        assertEquals("N", compassPoint(360f))
        assertEquals("W", compassPoint(-90f))
        assertEquals("E", compassPoint(450f))
    }
}
