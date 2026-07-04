package com.traffko.outlanderhub.vehicle.trip

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.traffko.outlanderhub.vehicle.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.tripDataStore by preferencesDataStore(name = "trip")

/**
 * Samples vehicle speed once a second and accumulates the current [Trip].
 * Persisted every [PERSIST_EVERY_MS] so a head-unit power cut loses at most
 * a few seconds; reset starts a fresh trip.
 */
class TripRepository(
    private val context: Context,
    private val vehicleState: StateFlow<VehicleState>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Keys {
        val STARTED_AT = longPreferencesKey("started_at")
        val DISTANCE_KM = doublePreferencesKey("distance_km")
        val MOVING_MS = longPreferencesKey("moving_ms")
    }

    private val _trip = MutableStateFlow(Trip())
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    init {
        scope.launch {
            val prefs = context.tripDataStore.data.first()
            val restored = Trip(
                startedAtMs = prefs[Keys.STARTED_AT] ?: System.currentTimeMillis(),
                distanceKm = prefs[Keys.DISTANCE_KM] ?: 0.0,
                movingMs = prefs[Keys.MOVING_MS] ?: 0L,
            )
            _trip.value = restored
            if (prefs[Keys.STARTED_AT] == null) persist(restored)

            var lastSampleAt = System.currentTimeMillis()
            var lastPersistAt = lastSampleAt
            while (true) {
                delay(SAMPLE_EVERY_MS)
                val now = System.currentTimeMillis()
                _trip.value = advanceTrip(_trip.value, vehicleState.value.speedKmh, now - lastSampleAt)
                lastSampleAt = now
                if (now - lastPersistAt >= PERSIST_EVERY_MS) {
                    lastPersistAt = now
                    persist(_trip.value)
                }
            }
        }
    }

    fun reset() {
        scope.launch {
            val fresh = Trip(startedAtMs = System.currentTimeMillis())
            _trip.value = fresh
            persist(fresh)
        }
    }

    private suspend fun persist(trip: Trip) {
        context.tripDataStore.edit {
            it[Keys.STARTED_AT] = trip.startedAtMs
            it[Keys.DISTANCE_KM] = trip.distanceKm
            it[Keys.MOVING_MS] = trip.movingMs
        }
    }

    private companion object {
        const val SAMPLE_EVERY_MS = 1_000L
        const val PERSIST_EVERY_MS = 30_000L
    }
}
