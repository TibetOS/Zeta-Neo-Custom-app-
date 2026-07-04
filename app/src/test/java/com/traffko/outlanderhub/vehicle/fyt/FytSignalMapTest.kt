package com.traffko.outlanderhub.vehicle.fyt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FytSignalMapTest {

    @Test
    fun `encode and decode round-trip the defaults`() {
        assertEquals(FytSignalMap.DEFAULTS, FytSignalMap.decode(FytSignalMap.encode(FytSignalMap.DEFAULTS)))
    }

    @Test
    fun `decode drops malformed entries and unknown kinds`() {
        val decoded = FytSignalMap.decode("16:DOORS,banana,99:NOT_A_SIGNAL,32:SPEED,:,7:")
        assertEquals(mapOf(16 to SignalKind.DOORS, 32 to SignalKind.SPEED), decoded)
    }

    @Test
    fun `decode of empty string is an empty map`() {
        assertTrue(FytSignalMap.decode("").isEmpty())
    }

    @Test
    fun `every signal kind has at most one default code`() {
        val kinds = FytSignalMap.DEFAULTS.values
        assertEquals(kinds.size, kinds.toSet().size)
    }
}
