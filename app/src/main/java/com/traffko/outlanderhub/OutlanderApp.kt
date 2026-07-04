package com.traffko.outlanderhub

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.traffko.outlanderhub.apps.InstalledAppsRepository
import com.traffko.outlanderhub.settings.SettingsRepository
import com.traffko.outlanderhub.vehicle.VehicleRepository

class OutlanderApp : Application() {

    lateinit var vehicleRepository: VehicleRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var installedAppsRepository: InstalledAppsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        vehicleRepository = VehicleRepository(this)
        settingsRepository = SettingsRepository(this)
        installedAppsRepository = InstalledAppsRepository(this)

        // Pause the demo simulation while another app is foreground; the FYT
        // binding stays alive so body alerts are current on return.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                vehicleRepository.setAppVisible(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                vehicleRepository.setAppVisible(false)
            }
        })
    }
}
