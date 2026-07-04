package com.traffko.outlanderhub.vehicle.fyt

import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.ClimateState
import com.traffko.outlanderhub.vehicle.DoorState
import com.traffko.outlanderhub.vehicle.VehicleState

/**
 * Pure mapping of raw CAN-module updates onto [VehicleState] per
 * [FytSignalMap]. Kept free of Android/binder dependencies so the decoding
 * rules — the part of the FYT integration most likely to need adjustment
 * after observing the real car — are unit-testable on the JVM.
 */
internal object FytSignalDecoder {

    /**
     * Returns [s] updated with whatever [e] carries. Any event at all proves
     * the decoder link is alive, so the result is always marked connected.
     */
    fun apply(s: VehicleState, e: BusEvent): VehicleState {
        val i0 = e.ints.getOrNull(0)
        val f0 = e.floats.getOrNull(0)
        return when (e.code) {
            FytSignalMap.CODE_SPEED -> s.copy(speedKmh = f0 ?: i0?.toFloat())
            FytSignalMap.CODE_RPM -> s.copy(rpm = i0)
            FytSignalMap.CODE_BATTERY_VOLTS -> s.copy(batteryVolts = f0 ?: i0?.let { it / 10f })
            FytSignalMap.CODE_COOLANT_TEMP -> s.copy(coolantTempC = f0 ?: i0?.toFloat())
            FytSignalMap.CODE_FUEL -> s.copy(fuelPercent = f0 ?: i0?.toFloat())
            FytSignalMap.CODE_OUTSIDE_TEMP -> s.copy(outsideTempC = f0 ?: i0?.toFloat())
            FytSignalMap.CODE_ODOMETER -> s.copy(odometerKm = i0)
            FytSignalMap.CODE_HANDBRAKE -> s.copy(handbrake = i0?.let { it != 0 })
            FytSignalMap.CODE_SEATBELT -> s.copy(seatbeltDriver = i0?.let { it != 0 })
            FytSignalMap.CODE_GEAR -> s.copy(gear = decodeGear(e.ints, e.strings) ?: s.gear)
            FytSignalMap.CODE_DOORS -> s.copy(doors = decodeDoors(e.ints))
            FytSignalMap.CODE_CLIMATE -> s.copy(climate = decodeClimate(e.ints, e.floats))
            FytSignalMap.CODE_TPMS -> s.copy(
                tirePressuresKpa = (0..3).map { idx -> e.floats.getOrNull(idx) ?: e.ints.getOrNull(idx)?.toFloat() }
            )
            else -> s
        }.copy(connected = true)
    }

    internal fun decodeDoors(ints: List<Int>): DoorState {
        // Common Raise-decoder layout: one bitmask int. Bit order to be
        // confirmed on the car (open a door, watch Diagnostics).
        val mask = ints.getOrNull(0) ?: return DoorState()
        return DoorState(
            frontLeft = mask and 0x01 != 0,
            frontRight = mask and 0x02 != 0,
            rearLeft = mask and 0x04 != 0,
            rearRight = mask and 0x08 != 0,
            trunk = mask and 0x10 != 0,
            hood = mask and 0x20 != 0,
        )
    }

    internal fun decodeClimate(ints: List<Int>, floats: List<Float>): ClimateState = ClimateState(
        acOn = ints.getOrNull(0)?.let { it != 0 },
        tempLeftC = floats.getOrNull(0) ?: ints.getOrNull(1)?.let { it / 2f },
        tempRightC = floats.getOrNull(1) ?: ints.getOrNull(2)?.let { it / 2f },
        fanSpeed = ints.getOrNull(3),
        recirculating = ints.getOrNull(4)?.let { it != 0 },
    )

    /**
     * Gear as either a string payload ("D", "drive", ...) or an index into
     * the common P/R/N/D ordering. Both to be confirmed on the car.
     */
    internal fun decodeGear(ints: List<Int>, strings: List<String>): String? {
        val s = strings.firstOrNull()?.trim()
        if (!s.isNullOrEmpty()) return s.take(1).uppercase()
        val idx = ints.firstOrNull() ?: return null
        return GEAR_BY_INDEX.getOrNull(idx)
    }

    private val GEAR_BY_INDEX = listOf("P", "R", "N", "D")
}
