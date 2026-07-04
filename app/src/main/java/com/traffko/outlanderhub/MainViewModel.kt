package com.traffko.outlanderhub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.traffko.outlanderhub.settings.AppSettings
import com.traffko.outlanderhub.vehicle.EventLog
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OutlanderApp
    private val vehicles = app.vehicleRepository
    private val settingsRepo = app.settingsRepository
    private val signalMapRepo = app.signalMapRepository

    val vehicleState: StateFlow<VehicleState> = vehicles.state
    val eventLog: StateFlow<EventLog> = vehicles.eventLog
    val activeSource: StateFlow<VehicleSource> = vehicles.activeSource
    val signalMap: StateFlow<Map<Int, SignalKind>> = signalMapRepo.map

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _lastExportPath = MutableStateFlow<String?>(null)
    val lastExportPath: StateFlow<String?> = _lastExportPath.asStateFlow()

    init {
        // Apply the persisted data source on startup and whenever it changes.
        viewModelScope.launch {
            settings.collect { vehicles.setSource(it.source) }
        }
    }

    fun setSource(source: VehicleSource) {
        viewModelScope.launch { settingsRepo.setSource(source) }
    }

    fun setShowDiagnostics(show: Boolean) {
        viewModelScope.launch { settingsRepo.setShowDiagnostics(show) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setOverlayEnabled(enabled) }
    }

    fun assignSignal(code: Int, kind: SignalKind?) {
        viewModelScope.launch { signalMapRepo.assign(code, kind) }
    }

    fun resetSignalMap() {
        viewModelScope.launch { signalMapRepo.resetToDefaults() }
    }

    fun exportLog() {
        viewModelScope.launch { _lastExportPath.value = vehicles.exportLog() }
    }

    fun sendCommand(code: Int, vararg ints: Int): Boolean =
        vehicles.sendCommand(code, ints)

    fun clearEventLog() = vehicles.clearEventLog()
}
