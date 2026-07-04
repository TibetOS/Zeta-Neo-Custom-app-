package com.traffko.outlanderhub.vehicle.fyt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.syu.ipc.IModuleCallback
import com.syu.ipc.IRemoteModule
import com.syu.ipc.IRemoteToolkit
import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.VehicleBus
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "FytVehicleBus"

/**
 * Live vehicle data from the Zeta Neo's FYT platform: binds the proprietary
 * `com.syu.ms` toolkit service and subscribes to its CAN-bus module, which is
 * fed by the head unit's Outlander CAN decoder box.
 *
 * EXPERIMENTAL: the toolkit interface is undocumented. Every raw update is
 * mirrored to [events] so the Diagnostics screen can be used on the real car
 * to confirm/adjust the code mapping in [FytSignalMap].
 */
class FytVehicleBus(private val context: Context) : VehicleBus {

    private val _state = MutableStateFlow(VehicleState(source = VehicleSource.FYT_CAN))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BusEvent>(extraBufferCapacity = 512)
    override val events: SharedFlow<BusEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registrationJob: Job? = null

    @Volatile private var toolkit: IRemoteToolkit? = null
    @Volatile private var canModule: IRemoteModule? = null

    // One connection object per start(): stop() unbinds asynchronously, and a
    // shared connection would let a stale unbind detach a binding created by
    // a subsequent start(). Callbacks compare against this field and ignore
    // anything arriving for a connection that has already been released.
    @Volatile private var connection: ToolkitConnection? = null

    private val callback = object : IModuleCallback.Stub() {
        override fun update(updateCode: Int, ints: IntArray?, flts: FloatArray?, strs: Array<String>?) {
            val event = BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "fyt-can",
                code = updateCode,
                ints = ints?.toList() ?: emptyList(),
                floats = flts?.toList() ?: emptyList(),
                strings = strs?.toList() ?: emptyList(),
            )
            _events.tryEmit(event)
            _state.update { FytSignalDecoder.apply(it, event) }
        }
    }

    private inner class ToolkitConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Toolkit service connected: $name")
            emitInfo("service connected: $name")
            if (connection !== this) {
                // stop() already released this connection — don't touch it.
                Log.w(TAG, "onServiceConnected on a released connection; ignoring")
                return
            }
            try {
                val tk = IRemoteToolkit.Stub.asInterface(service)
                toolkit = tk
                val module = tk?.getRemoteModule(FytProtocol.MODULE_CANBUS)
                canModule = module
                if (module == null) {
                    emitInfo("getRemoteModule(CANBUS) returned null")
                    return
                }
                // 256 binder round-trips — keep them off the main thread.
                registrationJob?.cancel()
                registrationJob = scope.launch {
                    var registered = 0
                    for (code in FytProtocol.CAN_SUBSCRIBE_FROM..FytProtocol.CAN_SUBSCRIBE_TO) {
                        if (!isActive) return@launch
                        try {
                            module.register(callback, code, 1)
                            registered++
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Service died during registration", e)
                            emitInfo("service died during registration at code=$code")
                            return@launch
                        } catch (e: Exception) {
                            Log.w(TAG, "register($code) failed", e)
                        }
                    }
                    emitInfo("registered for $registered CAN update codes")
                }
                _state.update { it.copy(connected = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Toolkit handshake failed", e)
                emitInfo("handshake failed: $e")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (connection !== this) return
            // The binding survives: BIND_AUTO_CREATE restarts the service and
            // onServiceConnected re-registers the callbacks.
            Log.w(TAG, "Toolkit service disconnected")
            emitInfo("service disconnected")
            toolkit = null
            canModule = null
            _state.update { it.copy(connected = false) }
        }

        override fun onBindingDied(name: ComponentName?) {
            if (connection !== this) return
            // Unlike a plain disconnect (host package updated/disabled), this
            // binding is dead for good — release it and bind again.
            Log.w(TAG, "Toolkit binding died; rebinding")
            emitInfo("binding died — rebinding")
            stop()
            start()
        }
    }

    override fun start() {
        if (connection != null) return
        val conn = ToolkitConnection()
        for (action in FytProtocol.SERVICE_ACTIONS) {
            val intent = Intent(action).setPackage(FytProtocol.HOST_PACKAGE)
            try {
                if (context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                    connection = conn
                    emitInfo("bindService OK with action=$action")
                    return
                }
                emitInfo("bindService returned false for action=$action")
            } catch (e: Exception) {
                emitInfo("bindService threw for action=$action: $e")
            }
            // Even a failed bindService leaves the connection registered;
            // release it before trying the next candidate action.
            runCatching { context.unbindService(conn) }
        }
        emitInfo("could not bind ${FytProtocol.HOST_PACKAGE} toolkit — is this an FYT unit?")
    }

    override fun stop() {
        val conn = connection ?: return
        connection = null
        registrationJob?.cancel()
        registrationJob = null
        val module = canModule
        toolkit = null
        canModule = null
        // Unregister is another burst of binder calls — do it (and the unbind
        // that must follow it) off the main thread. This only ever touches
        // `conn`, so a start() that runs meanwhile is unaffected.
        scope.launch {
            if (module != null) {
                for (code in FytProtocol.CAN_SUBSCRIBE_FROM..FytProtocol.CAN_SUBSCRIBE_TO) {
                    try {
                        module.unregister(callback, code)
                    } catch (e: RemoteException) {
                        break
                    } catch (_: Exception) {
                    }
                }
            }
            runCatching { context.unbindService(conn) }
                .onFailure { Log.w(TAG, "unbindService failed", it) }
        }
        _state.update { it.copy(connected = false) }
    }

    override fun sendCommand(code: Int, ints: IntArray, floats: FloatArray, strings: Array<String>): Boolean {
        val module = canModule ?: return false
        return try {
            module.cmd(code, ints, floats, strings)
            emitInfo("cmd($code) sent ints=${ints.toList()}")
            true
        } catch (e: Exception) {
            emitInfo("cmd($code) failed: $e")
            false
        }
    }

    private fun emitInfo(message: String) {
        _events.tryEmit(
            BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "fyt-info",
                code = -1,
                strings = listOf(message),
            )
        )
    }
}
