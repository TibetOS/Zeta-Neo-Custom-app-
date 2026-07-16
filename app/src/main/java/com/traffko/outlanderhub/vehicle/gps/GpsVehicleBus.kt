package com.traffko.outlanderhub.vehicle.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.VehicleBus
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Vehicle data from the head unit's GPS receiver — the source that works
 * before the CAN decoder is mapped. Provides speed (Doppler, with a
 * position-delta fallback), heading and altitude; everything body-related
 * stays null. Speed alone drives the dash numeral, the trip computer and the
 * floating overlay.
 *
 * Requires ACCESS_FINE_LOCATION at runtime; until granted the bus reports
 * disconnected and says so on the diagnostics log. [start] is idempotent and
 * re-checks the permission, so a retry after granting just works.
 */
class GpsVehicleBus(context: Context) : VehicleBus {

    // Application context: this bus lives as long as the repository, and must
    // not pin an Activity if one is ever passed in.
    private val context = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var staleJob: Job? = null
    private var registered = false
    private var lastLocation: Location? = null
    // Monotonic — wall time jumps when the unit syncs its clock after boot.
    private var lastFixAtMs = 0L

    private val _state = MutableStateFlow(VehicleState(source = VehicleSource.GPS))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BusEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<BusEvent> = _events.asSharedFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)

        // Deprecated, but abstract below API 30 — the unit's firmware may
        // still call it; a missing override would throw.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) {
            info("provider $provider enabled")
        }

        override fun onProviderDisabled(provider: String) {
            info("provider $provider disabled — enable location in system settings")
            _state.update { it.copy(speedKmh = null, headingDeg = null, gpsAccuracyM = null) }
        }
    }

    override fun start() {
        if (registered) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            info("no location service on this device")
            return
        }
        if (!hasPermission()) {
            info("location permission not granted — grant it from Setup → GPS")
            _state.value = VehicleState(source = VehicleSource.GPS, connected = false)
            return
        }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { lm.allProviders.contains(it) }
        if (provider == null) {
            info("no location provider available on this device")
            return
        }
        runCatching {
            lm.requestLocationUpdates(provider, FIX_INTERVAL_MS, 0f, listener, Looper.getMainLooper())
        }.onFailure { e ->
            info("requestLocationUpdates failed: ${e.message}")
            return
        }
        registered = true
        _state.value = VehicleState(source = VehicleSource.GPS, connected = true)
        info("listening to $provider — waiting for first fix (needs sky view)")

        // A frozen speed is worse than none: blank the motion fields when the
        // receiver goes quiet (tunnel, parking garage, antenna fault).
        staleJob = scope.launch {
            while (true) {
                delay(STALE_CHECK_MS)
                val stale = lastFixAtMs != 0L &&
                    SystemClock.elapsedRealtime() - lastFixAtMs > FIX_STALE_MS
                if (stale && _state.value.speedKmh != null) {
                    info("fix lost — waiting for satellites")
                    _state.update { it.copy(speedKmh = null, headingDeg = null, gpsAccuracyM = null) }
                }
            }
        }
    }

    override fun stop() {
        staleJob?.cancel()
        staleJob = null
        if (registered) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            runCatching { lm?.removeUpdates(listener) }
            registered = false
        }
        lastLocation = null
        lastFixAtMs = 0L
        _state.value = VehicleState(source = VehicleSource.GPS, connected = false)
    }

    override fun sendCommand(code: Int, ints: IntArray, floats: FloatArray, strings: Array<String>): Boolean =
        false

    private fun onFix(location: Location) {
        val nowMs = SystemClock.elapsedRealtime()
        val prev = lastLocation
        // dt from the fixes' own hardware timestamps, not receive time: queued
        // callbacks can arrive back-to-back and a near-zero receive delta would
        // fabricate a speed spike in the fallback path.
        val dtMs = if (prev == null) 0L
        else (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000L
        val rawKmh = when {
            location.hasSpeed() -> location.speed * MPS_TO_KMH
            prev != null && dtMs > 0 -> fallbackSpeedKmh(location.distanceTo(prev), dtMs)
            else -> null
        }
        val speedKmh = rawKmh?.let { clampCreepToZero(it) }
        lastLocation = location
        lastFixAtMs = nowMs

        _state.update {
            it.copy(
                connected = true,
                speedKmh = speedKmh,
                headingDeg = if (location.hasBearing() && (speedKmh ?: 0f) >= HEADING_MIN_KMH)
                    location.bearing else it.headingDeg,
                altitudeM = if (location.hasAltitude()) location.altitude.toFloat() else it.altitudeM,
                gpsAccuracyM = if (location.hasAccuracy()) location.accuracy else null,
            )
        }
        _events.tryEmit(
            BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "gps",
                code = CODE_FIX,
                // NaN for absent readings — Location getters return 0.0 when
                // unset, which would read as a real measurement in the log.
                floats = listOf(
                    speedKmh ?: Float.NaN,
                    if (location.hasBearing()) location.bearing else Float.NaN,
                    if (location.hasAltitude()) location.altitude.toFloat() else Float.NaN,
                    if (location.hasAccuracy()) location.accuracy else Float.NaN,
                ),
            )
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun info(message: String) {
        _events.tryEmit(
            BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "gps-info",
                code = -1,
                strings = listOf(message),
            )
        )
    }

    private companion object {
        const val FIX_INTERVAL_MS = 1_000L
        const val STALE_CHECK_MS = 5_000L
        const val FIX_STALE_MS = 10_000L
        /** Diagnostics-log code for a fix event: [speed km/h, bearing°, altitude m, accuracy m]. */
        const val CODE_FIX = 1
    }
}
