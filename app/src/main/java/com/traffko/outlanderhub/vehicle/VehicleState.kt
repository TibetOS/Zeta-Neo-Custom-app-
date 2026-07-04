package com.traffko.outlanderhub.vehicle

enum class VehicleSource { DEMO, FYT_CAN }

data class DoorState(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
) {
    val anyOpen: Boolean
        get() = frontLeft || frontRight || rearLeft || rearRight || trunk || hood
}

data class ClimateState(
    val acOn: Boolean? = null,
    val tempLeftC: Float? = null,
    val tempRightC: Float? = null,
    val fanSpeed: Int? = null,
    val recirculating: Boolean? = null,
)

/**
 * Single snapshot of everything we know about the car. Fields are nullable:
 * null means "this signal has not been seen from the current data source".
 */
data class VehicleState(
    val source: VehicleSource = VehicleSource.DEMO,
    val connected: Boolean = false,
    val speedKmh: Float? = null,
    val rpm: Int? = null,
    val coolantTempC: Float? = null,
    val batteryVolts: Float? = null,
    val fuelPercent: Float? = null,
    val outsideTempC: Float? = null,
    val odometerKm: Int? = null,
    val gear: String? = null,
    val handbrake: Boolean? = null,
    val seatbeltDriver: Boolean? = null,
    val doors: DoorState = DoorState(),
    val climate: ClimateState = ClimateState(),
    val tirePressuresKpa: List<Float?> = listOf(null, null, null, null),
)

/**
 * A raw event from the underlying bus, kept for the diagnostics screen so
 * unknown CAN-decoder signals can be observed and mapped.
 */
data class BusEvent(
    val timestampMs: Long,
    val channel: String,
    val code: Int,
    val ints: List<Int> = emptyList(),
    val floats: List<Float> = emptyList(),
    val strings: List<String> = emptyList(),
) {
    fun pretty(): String = buildString {
        append(channel).append(" code=").append(code)
        if (ints.isNotEmpty()) append(" ints=").append(ints.joinToString(",", "[", "]"))
        if (floats.isNotEmpty()) append(" flts=").append(floats.joinToString(",", "[", "]"))
        if (strings.isNotEmpty()) append(" strs=").append(strings.joinToString(",", "[", "]"))
    }
}
