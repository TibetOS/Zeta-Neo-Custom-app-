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
import com.traffko.outlanderhub.vehicle.ClimateState
import com.traffko.outlanderhub.vehicle.DoorState
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
    @Volatile private var bound = false

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
            applySignal(event)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Toolkit service connected: $name")
            emitInfo("service connected: $name")
            if (!bound) {
                // stop() already ran — don't touch the connection.
                Log.w(TAG, "onServiceConnected after stop(); ignoring")
                return
            }
            try {
                toolkit = IRemoteToolkit.Stub.asInterface(service)
                val module = toolkit?.getRemoteModule(FytProtocol.MODULE_CANBUS)
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
            Log.w(TAG, "Toolkit service disconnected")
            emitInfo("service disconnected")
            toolkit = null
            canModule = null
            _state.update { it.copy(connected = false) }
        }
    }

    override fun start() {
        if (bound) return
        for (action in FytProtocol.SERVICE_ACTIONS) {
            val intent = Intent(action).setPackage(FytProtocol.HOST_PACKAGE)
            try {
                if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    bound = true
                    emitInfo("bindService OK with action=$action")
                    return
                }
                emitInfo("bindService returned false for action=$action")
            } catch (e: Exception) {
                emitInfo("bindService threw for action=$action: $e")
            }
        }
        emitInfo("could not bind ${FytProtocol.HOST_PACKAGE} toolkit — is this an FYT unit?")
    }

    override fun stop() {
        if (!bound) return
        bound = false
        registrationJob?.cancel()
        registrationJob = null
        val module = canModule
        toolkit = null
        canModule = null
        // Unregister is another burst of binder calls — do it (and the unbind
        // that must follow it) off the main thread.
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
            runCatching { context.unbindService(connection) }
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

    /** Map a raw CAN-module update onto [VehicleState] per [FytSignalMap]. */
    private fun applySignal(e: BusEvent) {
        val i0 = e.ints.getOrNull(0)
        val f0 = e.floats.getOrNull(0)
        _state.update { s ->
            when (e.code) {
                FytSignalMap.CODE_SPEED -> s.copy(speedKmh = f0 ?: i0?.toFloat())
                FytSignalMap.CODE_RPM -> s.copy(rpm = i0)
                FytSignalMap.CODE_BATTERY_VOLTS -> s.copy(batteryVolts = f0 ?: i0?.let { it / 10f })
                FytSignalMap.CODE_COOLANT_TEMP -> s.copy(coolantTempC = f0 ?: i0?.toFloat())
                FytSignalMap.CODE_FUEL -> s.copy(fuelPercent = f0 ?: i0?.toFloat())
                FytSignalMap.CODE_OUTSIDE_TEMP -> s.copy(outsideTempC = f0 ?: i0?.toFloat())
                FytSignalMap.CODE_ODOMETER -> s.copy(odometerKm = i0)
                FytSignalMap.CODE_HANDBRAKE -> s.copy(handbrake = i0?.let { it != 0 })
                FytSignalMap.CODE_SEATBELT -> s.copy(seatbeltDriver = i0?.let { it != 0 })
                FytSignalMap.CODE_DOORS -> s.copy(doors = decodeDoors(e.ints))
                FytSignalMap.CODE_CLIMATE -> s.copy(climate = decodeClimate(e.ints, e.floats))
                FytSignalMap.CODE_TPMS -> s.copy(
                    tirePressuresKpa = (0..3).map { idx -> e.floats.getOrNull(idx) ?: e.ints.getOrNull(idx)?.toFloat() }
                )
                else -> s
            }.copy(connected = true)
        }
    }

    private fun decodeDoors(ints: List<Int>): DoorState {
        // Common Raise-decoder layout: one bitmask int. Bit order to be
        // confirmed on the car (open a door, watch Diagnostics).
        val mask = ints.getOrNull(0) ?: return DoorState()
        return DoorState(
            frontLeft = mask and 0x01 != 0,
            frontRight = mask and 0x02 != 0,
            rearLeft = mask and 0x04 != 0,
            rearRight = mask and 0x08 != 0,
            trunk = mask and 0x10 != 0,
            hood = mask and 0x20 != 0,
        )
    }

    private fun decodeClimate(ints: List<Int>, floats: List<Float>): ClimateState = ClimateState(
        acOn = ints.getOrNull(0)?.let { it != 0 },
        tempLeftC = floats.getOrNull(0) ?: ints.getOrNull(1)?.let { it / 2f },
        tempRightC = floats.getOrNull(1) ?: ints.getOrNull(2)?.let { it / 2f },
        fanSpeed = ints.getOrNull(3),
        recirculating = ints.getOrNull(4)?.let { it != 0 },
    )

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
