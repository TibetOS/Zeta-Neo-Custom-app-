package com.traffko.outlanderhub

import android.app.Application
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
    }
}
