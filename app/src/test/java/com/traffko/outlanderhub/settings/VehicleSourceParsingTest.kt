package com.traffko.outlanderhub.settings

import com.traffko.outlanderhub.vehicle.VehicleSource
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleSourceParsingTest {

    @Test
    fun `known names round-trip`() {
        assertEquals(VehicleSource.DEMO, parseVehicleSource("DEMO"))
        assertEquals(VehicleSource.FYT_CAN, parseVehicleSource("FYT_CAN"))
    }

    @Test
    fun `missing value falls back to demo`() {
        assertEquals(VehicleSource.DEMO, parseVehicleSource(null))
    }

    @Test
    fun `unknown value from an older or newer APK falls back to demo`() {
        assertEquals(VehicleSource.DEMO, parseVehicleSource("OBD_BLUETOOTH"))
        assertEquals(VehicleSource.DEMO, parseVehicleSource(""))
    }
}
