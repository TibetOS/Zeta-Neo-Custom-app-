package com.traffko.outlanderhub.vehicle.fyt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.signalMapDataStore by preferencesDataStore(name = "signal_map")

/**
 * The live CAN-code → signal mapping, editable at runtime from the CAN tab so
 * the decoder layout can be corrected in the parked car — no rebuild loop.
 * Seeded from [FytSignalMap.DEFAULTS]; persisted in DataStore.
 */
class SignalMapRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val MAP = stringPreferencesKey("map")
    }

    val map: StateFlow<Map<Int, SignalKind>> = context.signalMapDataStore.data
        .map { prefs -> prefs[Keys.MAP]?.let(FytSignalMap::decode) ?: FytSignalMap.DEFAULTS }
        .stateIn(scope, SharingStarted.Eagerly, FytSignalMap.DEFAULTS)

    /** Assign [code] to [kind]; a null [kind] clears the code's mapping. */
    suspend fun assign(code: Int, kind: SignalKind?) {
        edit { current ->
            if (kind == null) current - code
            // A signal can only live on one code — assigning it here removes
            // it from wherever it was.
            else current.filterValues { it != kind } + (code to kind)
        }
    }

    suspend fun resetToDefaults() {
        context.signalMapDataStore.edit { it.remove(Keys.MAP) }
    }

    private suspend fun edit(transform: (Map<Int, SignalKind>) -> Map<Int, SignalKind>) {
        context.signalMapDataStore.edit { prefs ->
            val current = prefs[Keys.MAP]?.let(FytSignalMap::decode) ?: FytSignalMap.DEFAULTS
            prefs[Keys.MAP] = FytSignalMap.encode(transform(current))
        }
    }
}
