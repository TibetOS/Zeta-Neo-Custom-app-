package com.traffko.outlanderhub

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.traffko.outlanderhub.apps.InstalledAppsRepository
import com.traffko.outlanderhub.overlay.OverlayService
import com.traffko.outlanderhub.settings.SettingsRepository
import com.traffko.outlanderhub.vehicle.VehicleRepository
import com.traffko.outlanderhub.vehicle.fyt.SignalMapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OutlanderApp : Application() {

    lateinit var vehicleRepository: VehicleRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var installedAppsRepository: InstalledAppsRepository
        private set
    lateinit var signalMapRepository: SignalMapRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appVisible = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        signalMapRepository = SignalMapRepository(this)
        vehicleRepository = VehicleRepository(this, signalMapRepository.map)
        settingsRepository = SettingsRepository(this)
        installedAppsRepository = InstalledAppsRepository(this)

        // Pause the demo simulation while another app is foreground; the FYT
        // binding stays alive so body alerts are current on return.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appVisible.value = true
                vehicleRepository.setAppVisible(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                appVisible.value = false
                vehicleRepository.setAppVisible(false)
            }
        })

        // Keep the floating overlay service in step with the setting. Starting
        // a foreground service is only allowed while the app is visible
        // (Android 12+), so starts are gated on visibility — the toggle lives
        // in our own Settings screen, and after a reboot the service starts
        // the first time the launcher comes to the foreground.
        appScope.launch {
            combine(
                settingsRepository.settings.map { it.overlayEnabled }.distinctUntilChanged(),
                appVisible,
            ) { enabled, visible -> enabled to visible }
                .collect { (enabled, visible) ->
                    when {
                        !enabled -> OverlayService.stop(this@OutlanderApp)
                        visible && Settings.canDrawOverlays(this@OutlanderApp) ->
                            OverlayService.start(this@OutlanderApp)
                    }
                }
        }
    }
}
