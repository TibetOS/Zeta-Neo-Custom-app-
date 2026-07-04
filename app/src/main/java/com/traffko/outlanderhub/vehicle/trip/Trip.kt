package com.traffko.outlanderhub.vehicle.trip

/**
 * Accumulated trip stats, derived purely from sampled vehicle speed:
 * distance is the time-integral of speed, moving time counts samples above
 * walking pace, average speed is distance over moving time.
 */
data class Trip(
    val startedAtMs: Long = 0L,
    val distanceKm: Double = 0.0,
    val movingMs: Long = 0L,
) {
    val avgSpeedKmh: Double
        get() = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else 0.0
}

/**
 * Advances [trip] by one speed sample covering [dtMs]. Pure so the
 * integration rules are unit-testable.
 *
 * Samples spanning a gap longer than [MAX_SAMPLE_GAP_MS] (app paused, unit
 * asleep) contribute nothing: speed during the gap is unknown, and
 * integrating across it would fabricate distance.
 */
fun advanceTrip(trip: Trip, speedKmh: Float?, dtMs: Long): Trip {
    if (dtMs <= 0 || dtMs > MAX_SAMPLE_GAP_MS) return trip
    val speed = speedKmh ?: return trip
    val moving = speed > MOVING_THRESHOLD_KMH
    return trip.copy(
        distanceKm = trip.distanceKm + speed * dtMs / 3_600_000.0,
        movingMs = trip.movingMs + if (moving) dtMs else 0L,
    )
}

const val MAX_SAMPLE_GAP_MS = 5_000L
private const val MOVING_THRESHOLD_KMH = 2f
