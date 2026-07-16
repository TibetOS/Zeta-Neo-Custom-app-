package com.traffko.outlanderhub.vehicle.gps

/**
 * Pure math for the GPS source, kept free of android.location so the rules
 * are unit-testable on the JVM.
 */

internal const val MPS_TO_KMH = 3.6f

/**
 * GPS Doppler speed jitters around 1–2 km/h at a standstill; snap creep to a
 * clean zero so the dash reads 0 when parked and the trip computer doesn't
 * accumulate phantom metres at red lights.
 */
internal fun clampCreepToZero(speedKmh: Float, threshold: Float = CREEP_KMH): Float =
    if (speedKmh < threshold) 0f else speedKmh

/**
 * Position-delta fallback for fixes without a Doppler speed. Returns null for
 * implausible jumps (multipath / cold-start teleports) rather than a spike
 * that would corrupt the trip integral.
 */
internal fun fallbackSpeedKmh(distanceM: Float, dtMs: Long): Float? {
    if (dtMs <= 0) return null
    val kmh = distanceM / (dtMs / 1000f) * MPS_TO_KMH
    return kmh.takeIf { it <= MAX_PLAUSIBLE_KMH }
}

/** 315° → "NW", any input normalized into [0°, 360°). */
fun compassPoint(bearingDeg: Float): String {
    val normalized = ((bearingDeg % 360f) + 360f) % 360f
    return COMPASS_POINTS[((normalized + 22.5f) / 45f).toInt() % 8]
}

private val COMPASS_POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** Below this the reading is standstill noise, not motion. */
internal const val CREEP_KMH = 2f

/** Faster than anything the car can do — treat as a bad fix. */
internal const val MAX_PLAUSIBLE_KMH = 300f

/** Bearing is meaningless while crawling; hold the last good heading below this. */
internal const val HEADING_MIN_KMH = 5f
