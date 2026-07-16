package com.traffko.outlanderhub.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.traffko.outlanderhub.vehicle.VehicleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val source: VehicleSource = VehicleSource.DEMO,
    val showDiagnostics: Boolean = true,
    val overlayEnabled: Boolean = false,
    /** Pinned CarPlay/projection app; null = auto-detect a known client. */
    val projectionPackage: String? = null,
    /** Launch the projection app when the unit boots into the launcher. */
    val projectionAutoLaunch: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOURCE = stringPreferencesKey("vehicle_source")
        val SHOW_DIAGNOSTICS = booleanPreferencesKey("show_diagnostics")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val PROJECTION_PACKAGE = stringPreferencesKey("projection_package")
        val PROJECTION_AUTO_LAUNCH = booleanPreferencesKey("projection_auto_launch")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            source = parseVehicleSource(prefs[Keys.SOURCE]),
            showDiagnostics = prefs[Keys.SHOW_DIAGNOSTICS] ?: true,
            overlayEnabled = prefs[Keys.OVERLAY_ENABLED] ?: false,
            projectionPackage = prefs[Keys.PROJECTION_PACKAGE]?.takeIf { it.isNotEmpty() },
            projectionAutoLaunch = prefs[Keys.PROJECTION_AUTO_LAUNCH] ?: false,
        )
    }

    suspend fun setSource(source: VehicleSource) {
        context.dataStore.edit { it[Keys.SOURCE] = source.name }
    }

    suspend fun setShowDiagnostics(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DIAGNOSTICS] = show }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY_ENABLED] = enabled }
    }

    suspend fun setProjectionPackage(packageName: String?) {
        context.dataStore.edit { it[Keys.PROJECTION_PACKAGE] = packageName ?: "" }
    }

    suspend fun setProjectionAutoLaunch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROJECTION_AUTO_LAUNCH] = enabled }
    }
}

/** Falls back to DEMO for missing or unknown values (e.g. from an older APK). */
internal fun parseVehicleSource(raw: String?): VehicleSource =
    raw?.let { name -> VehicleSource.entries.firstOrNull { it.name == name } }
        ?: VehicleSource.DEMO
