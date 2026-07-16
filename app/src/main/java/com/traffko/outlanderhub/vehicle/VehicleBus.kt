package com.traffko.outlanderhub.vehicle

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A source of live vehicle data. Implementations:
 *  - [DemoVehicleBus]: simulated data for developing/testing the UI off the car.
 *  - [com.traffko.outlanderhub.vehicle.fyt.FytVehicleBus]: real data from the
 *    Zeta Neo (FYT platform) CAN-decoder service.
 *  - [com.traffko.outlanderhub.vehicle.gps.GpsVehicleBus]: speed/heading/altitude
 *    from the unit's GPS — works before the CAN decoder is mapped.
 */
interface VehicleBus {
    val state: StateFlow<VehicleState>

    /** Raw, unmapped events for the diagnostics screen. */
    val events: SharedFlow<BusEvent>

    fun start()
    fun stop()

    /**
     * Send a raw command to the bus (used by the controls and diagnostics
     * screens). Not all sources support commands; returns false if unsupported.
     */
    fun sendCommand(code: Int, ints: IntArray = intArrayOf(), floats: FloatArray = floatArrayOf(), strings: Array<String> = arrayOf()): Boolean
}
