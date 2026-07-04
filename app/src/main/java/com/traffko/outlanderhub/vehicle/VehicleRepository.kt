package com.traffko.outlanderhub.vehicle

import android.content.Context
import com.traffko.outlanderhub.vehicle.fyt.FytVehicleBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _eventLog = MutableStateFlow<List<BusEvent>>(emptyList())
    val eventLog: StateFlow<List<BusEvent>> = _eventLog.asStateFlow()

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

    fun sendCommand(code: Int, ints: IntArray = intArrayOf()): Boolean =
        activeBus.sendCommand(code, ints)

    fun clearEventLog() {
        _eventLog.value = emptyList()
    }

    private fun startPumps(bus: VehicleBus) {
        pumpJobs += scope.launch {
            bus.state.collect { _state.value = it }
        }
        pumpJobs += scope.launch {
            bus.events.collect { event ->
                _eventLog.update { log -> (log + event).takeLast(MAX_LOG) }
            }
        }
    }

    private fun stopPumps() {
        pumpJobs.forEach { it.cancel() }
        pumpJobs.clear()
    }

    private companion object {
        const val MAX_LOG = 400
    }
}
