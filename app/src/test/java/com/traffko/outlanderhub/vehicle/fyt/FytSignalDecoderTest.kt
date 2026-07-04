package com.traffko.outlanderhub.vehicle.fyt

import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.DoorState
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the decoding assumptions that must survive the in-car mapping
 * sessions — if a signal layout is corrected in FytSignalMap/FytSignalDecoder,
 * these tests document what changed.
 */
class FytSignalDecoderTest {

    private val base = VehicleState(source = VehicleSource.FYT_CAN)

    private fun event(
        code: Int,
        ints: List<Int> = emptyList(),
        floats: List<Float> = emptyList(),
        strings: List<String> = emptyList(),
    ) = BusEvent(timestampMs = 0L, channel = "test", code = code, ints = ints, floats = floats, strings = strings)

    @Test
    fun `any event marks the bus connected`() {
        val out = FytSignalDecoder.apply(base, event(code = 9999))
        assertTrue(out.connected)
        assertEquals(base, out.copy(connected = false))
    }

    @Test
    fun `speed prefers float payload over int`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_SPEED, ints = listOf(88), floats = listOf(64.5f)))
        assertEquals(64.5f, out.speedKmh)
    }

    @Test
    fun `speed falls back to int payload`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_SPEED, ints = listOf(88)))
        assertEquals(88f, out.speedKmh)
    }

    @Test
    fun `battery int payload is tenths of a volt`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_BATTERY_VOLTS, ints = listOf(142)))
        assertEquals(14.2f, out.batteryVolts)
    }

    @Test
    fun `empty payload leaves signal unknown`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_RPM))
        assertNull(out.rpm)
    }

    @Test
    fun `doors decode from bitmask`() {
        assertEquals(
            DoorState(frontLeft = true, trunk = true),
            FytSignalDecoder.decodeDoors(listOf(0x01 or 0x10)),
        )
        assertEquals(
            DoorState(frontRight = true, rearLeft = true, rearRight = true, hood = true),
            FytSignalDecoder.decodeDoors(listOf(0x02 or 0x04 or 0x08 or 0x20)),
        )
        assertFalse(FytSignalDecoder.decodeDoors(emptyList()).anyOpen)
    }

    @Test
    fun `climate decodes int layout with half-degree temps`() {
        val c = FytSignalDecoder.decodeClimate(ints = listOf(1, 44, 45, 3, 0), floats = emptyList())
        assertEquals(true, c.acOn)
        assertEquals(22.0f, c.tempLeftC)
        assertEquals(22.5f, c.tempRightC)
        assertEquals(3, c.fanSpeed)
        assertEquals(false, c.recirculating)
    }

    @Test
    fun `climate prefers float temps when present`() {
        val c = FytSignalDecoder.decodeClimate(ints = listOf(1, 44, 45), floats = listOf(21.5f, 23.0f))
        assertEquals(21.5f, c.tempLeftC)
        assertEquals(23.0f, c.tempRightC)
    }

    @Test
    fun `gear decodes string payload first`() {
        assertEquals("D", FytSignalDecoder.decodeGear(ints = listOf(0), strings = listOf("drive")))
    }

    @Test
    fun `gear decodes PRND index`() {
        assertEquals("P", FytSignalDecoder.decodeGear(ints = listOf(0), strings = emptyList()))
        assertEquals("D", FytSignalDecoder.decodeGear(ints = listOf(3), strings = emptyList()))
        assertNull(FytSignalDecoder.decodeGear(ints = listOf(9), strings = emptyList()))
        assertNull(FytSignalDecoder.decodeGear(ints = emptyList(), strings = emptyList()))
    }

    @Test
    fun `undecodable gear payload keeps the previous gear`() {
        val driving = base.copy(gear = "D")
        val out = FytSignalDecoder.apply(driving, event(FytSignalMap.CODE_GEAR, ints = listOf(9)))
        assertEquals("D", out.gear)
    }

    @Test
    fun `tpms fills four corners and pads missing ones with null`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_TPMS, floats = listOf(230f, 231f, 228f)))
        assertEquals(listOf(230f, 231f, 228f, null), out.tirePressuresKpa)
    }

    @Test
    fun `tpms falls back to int payload`() {
        val out = FytSignalDecoder.apply(base, event(FytSignalMap.CODE_TPMS, ints = listOf(230, 231, 228, 229)))
        assertEquals(listOf(230f, 231f, 228f, 229f), out.tirePressuresKpa)
    }
}
