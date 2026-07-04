package com.traffko.outlanderhub.vehicle

import android.content.Context
import com.traffko.outlanderhub.vehicle.fyt.FytVehicleBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the active [VehicleBus], allows hot-switching between the demo source
 * and the real FYT CAN source, and keeps a bounded log of raw bus events for
 * the Diagnostics screen.
 */
class VehicleRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val demoBus = DemoVehicleBus()
    private val fytBus = FytVehicleBus(context)

    private var activeBus: VehicleBus = demoBus
    private var pumpJobs = mutableListOf<Job>()

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    // Raw events arrive at CAN-bus rates, so they are buffered here and the
    // public snapshot is published at most every LOG_PUBLISH_MS — copying the
    // full list per event would churn allocations and recompose the
    // diagnostics list for every frame on the bus.
    private val logLock = Any()
    private val logBuffer = ArrayDeque<BusEvent>()
    private var logGeneration = 0L
    private val logDirty = Channel<Unit>(Channel.CONFLATED)

    private val _eventLog = MutableStateFlow(EventLog())
    val eventLog: StateFlow<EventLog> = _eventLog.asStateFlow()

    private val _activeSource = MutableStateFlow(VehicleSource.DEMO)
    val activeSource: StateFlow<VehicleSource> = _activeSource.asStateFlow()

    fun setSource(source: VehicleSource) {
        if (source == _activeSource.value && pumpJobs.isNotEmpty()) return
        stopPumps()
        activeBus.stop()
        activeBus = when (source) {
            VehicleSource.DEMO -> demoBus
            VehicleSource.FYT_CAN -> fytBus
        }
        _activeSource.value = source
        activeBus.start()
        startPumps(activeBus)
    }

    /**
     * Called from the process lifecycle. The FYT binding deliberately stays
     * alive in the background (so door/body alerts are current the moment the
     * user returns), but the demo simulation has no reason to burn CPU while
     * another app is foreground.
     */
    fun setAppVisible(visible: Boolean) {
        if (visible) {
            activeBus.start()
        } else if (activeBus === demoBus) {
            demoBus.stop()
        }
    }

    fun sendCommand(code: Int, ints: IntArray = intArrayOf()): Boolean =
        activeBus.sendCommand(code, ints)

    fun clearEventLog() {
        synchronized(logLock) {
            logBuffer.clear()
            logGeneration++
        }
        publishLog()
    }

    private fun startPumps(bus: VehicleBus) {
        pumpJobs += scope.launch {
            bus.state.collect { _state.value = it }
        }
        pumpJobs += scope.launch {
            bus.events.collect { event ->
                synchronized(logLock) {
                    logBuffer.addLast(event)
                    if (logBuffer.size > MAX_LOG) logBuffer.removeFirst()
                    logGeneration++
                }
                logDirty.trySend(Unit)
            }
        }
        pumpJobs += scope.launch {
            // Publishes immediately on the first event after a quiet spell,
            // then at most every LOG_PUBLISH_MS while the bus is chatty.
            for (dirty in logDirty) {
                publishLog()
                delay(LOG_PUBLISH_MS)
            }
        }
    }

    private fun publishLog() {
        _eventLog.value = synchronized(logLock) { EventLog(logBuffer.toList(), logGeneration) }
    }

    private fun stopPumps() {
        pumpJobs.forEach { it.cancel() }
        pumpJobs.clear()
    }

    private companion object {
        const val MAX_LOG = 400
        const val LOG_PUBLISH_MS = 100L
    }
}
