package com.traffko.outlanderhub.vehicle.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTest {

    @Test
    fun `distance integrates speed over time`() {
        // 60 km/h for 60 one-second samples = 1 km.
        var trip = Trip(startedAtMs = 0L)
        repeat(60) { trip = advanceTrip(trip, 60f, 1_000L) }
        assertEquals(1.0, trip.distanceKm, 1e-9)
        assertEquals(60_000L, trip.movingMs)
    }

    @Test
    fun `standing still accumulates no distance or moving time`() {
        var trip = Trip(startedAtMs = 0L)
        repeat(30) { trip = advanceTrip(trip, 0f, 1_000L) }
        assertEquals(0.0, trip.distanceKm, 1e-9)
        assertEquals(0L, trip.movingMs)
    }

    @Test
    fun `unknown speed contributes nothing`() {
        val trip = advanceTrip(Trip(startedAtMs = 0L), null, 1_000L)
        assertEquals(Trip(startedAtMs = 0L), trip)
    }

    @Test
    fun `samples across a long gap are skipped`() {
        // App paused for a minute: speed during the gap is unknown.
        val before = Trip(startedAtMs = 0L, distanceKm = 5.0, movingMs = 300_000L)
        assertEquals(before, advanceTrip(before, 80f, 60_000L))
    }

    @Test
    fun `average speed is distance over moving time`() {
        var trip = Trip(startedAtMs = 0L)
        repeat(30) { trip = advanceTrip(trip, 100f, 1_000L) } // fast
        repeat(30) { trip = advanceTrip(trip, 20f, 1_000L) }  // slow
        assertEquals(60.0, trip.avgSpeedKmh, 0.1)
    }

    @Test
    fun `average speed of an empty trip is zero`() {
        assertEquals(0.0, Trip().avgSpeedKmh, 0.0)
    }
}
