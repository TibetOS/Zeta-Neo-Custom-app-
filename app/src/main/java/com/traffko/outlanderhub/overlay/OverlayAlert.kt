package com.traffko.outlanderhub.overlay

import com.traffko.outlanderhub.vehicle.VehicleState

/**
 * What the floating overlay should shout about, if anything. Pure so the
 * priorities and thresholds are unit-testable; thresholds match the ones the
 * Dash/Car screens use for their danger tones.
 */
internal fun overlayAlert(s: VehicleState): String? {
    val moving = (s.speedKmh ?: 0f) > MOVING_KMH
    return when {
        s.doors.anyOpen && moving -> "Door open"
        s.seatbeltDriver == false && moving -> "Seatbelt unfastened"
        s.coolantTempC != null && s.coolantTempC >= COOLANT_DANGER_C -> "Coolant hot"
        s.tirePressuresKpa.any { it != null && it < TIRE_DANGER_KPA } -> "Low tire pressure"
        else -> null
    }
}

private const val MOVING_KMH = 5f
private const val COOLANT_DANGER_C = 115f
private const val TIRE_DANGER_KPA = 190f
