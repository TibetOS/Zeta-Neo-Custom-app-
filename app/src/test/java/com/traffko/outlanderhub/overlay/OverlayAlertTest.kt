package com.traffko.outlanderhub.overlay

import com.traffko.outlanderhub.vehicle.DoorState
import com.traffko.outlanderhub.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayAlertTest {

    private val parked = VehicleState(speedKmh = 0f)
    private val moving = VehicleState(speedKmh = 50f)

    @Test
    fun `no alert on a healthy state`() {
        assertNull(overlayAlert(moving.copy(seatbeltDriver = true, coolantTempC = 90f)))
    }

    @Test
    fun `door open while moving alerts, while parked does not`() {
        val openDoor = DoorState(frontLeft = true)
        assertEquals("Door open", overlayAlert(moving.copy(doors = openDoor)))
        assertNull(overlayAlert(parked.copy(doors = openDoor)))
    }

    @Test
    fun `seatbelt only matters while moving and only when explicitly unfastened`() {
        assertEquals("Seatbelt unfastened", overlayAlert(moving.copy(seatbeltDriver = false)))
        assertNull(overlayAlert(parked.copy(seatbeltDriver = false)))
        assertNull(overlayAlert(moving.copy(seatbeltDriver = null)))
    }

    @Test
    fun `hot coolant alerts regardless of speed`() {
        assertEquals("Coolant hot", overlayAlert(parked.copy(coolantTempC = 118f)))
        assertNull(overlayAlert(parked.copy(coolantTempC = 114f)))
    }

    @Test
    fun `low tire pressure alerts, unknown pressures do not`() {
        assertEquals(
            "Low tire pressure",
            overlayAlert(parked.copy(tirePressuresKpa = listOf(230f, 180f, null, 231f))),
        )
        assertNull(overlayAlert(parked.copy(tirePressuresKpa = listOf(null, null, null, null))))
    }

    @Test
    fun `door open outranks other alerts while moving`() {
        val state = moving.copy(
            doors = DoorState(trunk = true),
            seatbeltDriver = false,
            coolantTempC = 120f,
        )
        assertEquals("Door open", overlayAlert(state))
    }

    @Test
    fun `unknown speed counts as not moving`() {
        assertNull(overlayAlert(VehicleState(speedKmh = null, doors = DoorState(frontLeft = true))))
    }
}
