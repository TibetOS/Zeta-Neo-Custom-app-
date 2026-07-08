package com.traffko.outlanderhub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.traffko.outlanderhub.settings.AppSettings
import com.traffko.outlanderhub.update.ReleaseInfo
import com.traffko.outlanderhub.update.UpdateChecker
import com.traffko.outlanderhub.vehicle.EventLog
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
import com.traffko.outlanderhub.vehicle.trip.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data object Failed : UpdateStatus
    data class Available(val release: ReleaseInfo) : UpdateStatus
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OutlanderApp
    private val vehicles = app.vehicleRepository
    private val settingsRepo = app.settingsRepository
    private val signalMapRepo = app.signalMapRepository
    private val trips = app.tripRepository

    val vehicleState: StateFlow<VehicleState> = vehicles.state
    val eventLog: StateFlow<EventLog> = vehicles.eventLog
    val activeSource: StateFlow<VehicleSource> = vehicles.activeSource
    val signalMap: StateFlow<Map<Int, SignalKind>> = signalMapRepo.map
    val trip: StateFlow<Trip> = trips.trip

    /** Installed versionName, for the About card and update comparison. */
    val currentVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }
            .getOrNull() ?: "unknown"

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

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
        // Explicit user selection — unlike the persisted-source replay in init,
        // this is allowed to clear the Topway crash-loop latch for one attempt.
        if (source == VehicleSource.TOPWAY_TW) vehicles.rearmTopway()
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

    fun resetTrip() = trips.reset()

    fun checkForUpdates() {
        if (_updateStatus.value == UpdateStatus.Checking) return
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            val latest = UpdateChecker.fetchLatest()
            _updateStatus.value = when {
                latest == null -> UpdateStatus.Failed
                UpdateChecker.isNewer(currentVersion, latest.tag) -> UpdateStatus.Available(latest)
                else -> UpdateStatus.UpToDate
            }
        }
    }

    fun sendCommand(code: Int, vararg ints: Int): Boolean =
        vehicles.sendCommand(code, ints)

    fun probeFytConfig() = vehicles.probeFytConfig()

    fun clearEventLog() = vehicles.clearEventLog()
}
