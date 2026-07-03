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
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOURCE = stringPreferencesKey("vehicle_source")
        val SHOW_DIAGNOSTICS = booleanPreferencesKey("show_diagnostics")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            source = prefs[Keys.SOURCE]?.let { runCatching { VehicleSource.valueOf(it) }.getOrNull() }
                ?: VehicleSource.DEMO,
            showDiagnostics = prefs[Keys.SHOW_DIAGNOSTICS] ?: true,
        )
    }

    suspend fun setSource(source: VehicleSource) {
        context.dataStore.edit { it[Keys.SOURCE] = source.name }
    }

    suspend fun setShowDiagnostics(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DIAGNOSTICS] = show }
    }
}
