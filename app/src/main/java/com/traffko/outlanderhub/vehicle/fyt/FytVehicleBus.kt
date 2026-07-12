package com.traffko.outlanderhub.vehicle.fyt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.syu.ipc.IModuleCallback
import com.syu.ipc.IRemoteModule
import com.syu.ipc.IRemoteToolkit
import com.syu.ipc.ModuleObject
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

// null arrays and empty arrays are different facts when probing an
// undocumented service — print exactly what came back.
private fun ModuleObject.pretty(): String =
    "ints=${ints?.contentToString()} flts=${flts?.contentToString()} strs=${strs?.contentToString()}"

/**
 * Live vehicle data from the Zeta Neo's FYT platform: binds the proprietary
 * `com.syu.ms` toolkit service and subscribes to its CAN-bus module, which is
 * fed by the head unit's Outlander CAN decoder box.
 *
 * EXPERIMENTAL: the toolkit interface is undocumented. Every raw update is
 * mirrored to [events] so the Diagnostics screen can be used on the real car
 * to confirm/adjust the code mapping in [FytSignalMap].
 */
class FytVehicleBus(
    private val context: Context,
    private val signalMap: StateFlow<Map<Int, SignalKind>>,
) : VehicleBus {

    private val _state = MutableStateFlow(VehicleState(source = VehicleSource.FYT_CAN))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    // replay: start() emits its one-shot bind diagnostics synchronously,
    // before VehicleRepository's log collector attaches on a source switch.
    // Without replay those lines — the only clue when binding fails — reach no
    // subscriber and are lost, leaving the CAN tab blank.
    private val _events = MutableSharedFlow<BusEvent>(replay = 128, extraBufferCapacity = 512)
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
            _state.update { FytSignalDecoder.apply(it, event, signalMap.value) }
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

    // start/stop are synchronized: all current callers are main-thread
    // confined, but nothing in the VehicleBus contract promises that, and an
    // interleaved start/start or start/stop would double-bind or strand a
    // connection.
    override fun start(): Unit = synchronized(this) {
        if (connection != null) return
        val conn = ToolkitConnection()

        // Explicit component is the reference client's bind path and the most
        // reliable — action-only binds are flaky on FYT firmwares.
        val toolkitComponent = ComponentName(FytProtocol.HOST_PACKAGE, FytProtocol.TOOLKIT_COMPONENT)
        if (tryBind(
                conn,
                Intent(FytProtocol.SERVICE_ACTIONS.first()).setComponent(toolkitComponent),
                "component=${FytProtocol.HOST_PACKAGE}/${FytProtocol.TOOLKIT_COMPONENT}",
            )
        ) return

        // Known intent actions next — fallback for firmwares that name the
        // service differently but still respond to the action.
        for (action in FytProtocol.SERVICE_ACTIONS) {
            if (tryBind(conn, Intent(action).setPackage(FytProtocol.HOST_PACKAGE), "action=$action")) return
        }

        // Unknown firmware: enumerate whatever services the host package
        // actually declares and bind them by explicit component. This both
        // surfaces what the unit exposes (in Diagnostics) and can connect
        // without a hardcoded action.
        val components = hostServiceComponents()
        if (components.isNotEmpty()) {
            emitInfo("trying ${components.size} ${FytProtocol.HOST_PACKAGE} service component(s)")
            for (component in components) {
                if (tryBind(conn, Intent().setComponent(component), "component=${component.shortClassName}")) return
            }
            emitInfo("bound to none of ${FytProtocol.HOST_PACKAGE}'s services — the toolkit may not be exported on this firmware")
        }

        // Nothing bound (likely not a com.syu.ms unit): enumerate what CAN /
        // vehicle services this firmware DOES expose, to find the real host.
        scanForCandidateServices()
    }

    /** One bind attempt; on success records [connection] and returns true. */
    private fun tryBind(conn: ToolkitConnection, intent: Intent, label: String): Boolean {
        try {
            if (context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                connection = conn
                emitInfo("bindService OK with $label")
                return true
            }
            emitInfo("bindService returned false for $label")
        } catch (e: Exception) {
            emitInfo("bindService threw for $label: $e")
        }
        // Even a failed bindService leaves the connection registered; release
        // it before the next attempt.
        runCatching { context.unbindService(conn) }
        return false
    }

    /** Service components declared by the host package, for component-based binding. */
    private fun hostServiceComponents(): List<ComponentName> = try {
        val info = context.packageManager.getPackageInfo(FytProtocol.HOST_PACKAGE, PackageManager.GET_SERVICES)
        info.services?.map { ComponentName(it.packageName, it.name) } ?: emptyList()
    } catch (e: PackageManager.NameNotFoundException) {
        emitInfo("${FytProtocol.HOST_PACKAGE} is NOT installed — this unit may not run the FYT toolkit")
        emptyList()
    } catch (e: Exception) {
        emitInfo("could not query ${FytProtocol.HOST_PACKAGE} services: $e")
        emptyList()
    }

    override fun stop(): Unit = synchronized(this) {
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

    /**
     * Pull-mode read of one toolkit value (`IRemoteModule.get`) — the one-shot
     * counterpart of the register() push subscription. Blocking binder call:
     * keep off the main thread. Null when the module is unbound or the call
     * fails.
     */
    fun readCode(code: Int): ModuleObject? {
        val module = canModule ?: return null
        return try {
            module.get(code, null, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "get($code) failed", e)
            null
        }
    }

    /**
     * get()-probe the decoder's static config codes and report each result as
     * an fyt-info line. These values are never pushed, so this is the only way
     * they show up in the CAN tab. Binder round-trips — runs on the bus scope.
     */
    fun probeConfig() {
        scope.launch {
            if (canModule == null) {
                emitInfo("config probe skipped — CAN module not bound")
                return@launch
            }
            for ((code, name) in FytProtocol.CONFIG_PROBE_CODES) {
                val result = readCode(code)
                emitInfo("get($name=$code) → ${result?.pretty() ?: "null"}")
            }
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

    /**
     * With no com.syu.ms toolkit present, list packages/services on this unit
     * whose name hints at a CAN or vehicle bridge, so the real host can be
     * spotted in the CAN tab and targeted in FytProtocol.
     */
    private fun scanForCandidateServices() {
        val pm = context.packageManager
        val withServices = try {
            pm.getInstalledPackages(PackageManager.GET_SERVICES)
        } catch (e: Exception) {
            emitInfo("service scan hit ${e.javaClass.simpleName}; falling back to package names")
            null
        }
        if (withServices != null) {
            emitInfo("scanning ${withServices.size} packages for a CAN/vehicle service")
            var hits = 0
            for (pkg in withServices) {
                val matched = pkg.services?.filter { svc ->
                    CAN_KEYWORDS.any { svc.name.contains(it, true) || pkg.packageName.contains(it, true) }
                }.orEmpty()
                if (matched.isNotEmpty()) {
                    hits++
                    emitInfo("${pkg.packageName} → ${matched.joinToString { it.name.substringAfterLast('.') }}")
                }
            }
            emitInfo(
                if (hits == 0) "no CAN/vehicle service matched by keyword — running Topway platform probe"
                else "found $hits candidate package(s) above",
            )
            // This unit is a Topway TS-class unit, not FYT: its CAN path is the
            // TWUtil serial mux, invisible to a service/keyword scan. Probe it,
            // and deep-inspect any Topway vehicle package it confirms present.
            runTopwayProbe()
            return
        }

        // Fallback: names only, to avoid TransactionTooLargeException on big lists.
        runTopwayProbe()
    }

    private fun runTopwayProbe() {
        TopwayProbe(context, ::emitInfo, ::probeHostCandidate).run()
    }

    /**
     * Deep-inspect a candidate CAN-host package: list its providers (with
     * authorities), services and receivers, note vehicle-ish permissions, and
     * try to bind each exported service — the bound Binder's interface
     * descriptor names its AIDL, which tells us how to talk to it. Diagnostic.
     */
    private fun probeHostCandidate(pkg: String) {
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        val info = try {
            context.packageManager.getPackageInfo(pkg, flags)
        } catch (e: Exception) {
            emitInfo("cannot inspect $pkg: ${e.javaClass.simpleName}")
            return
        }
        emitInfo("── inspecting $pkg ──")
        info.providers?.forEach {
            emitInfo("provider ${it.name.substringAfterLast('.')} auth=${it.authority} exported=${it.exported}")
        }
        info.services?.forEach {
            // .permission names the gate — a signature perm here confirms the
            // "platform-signed consumer only" finding on carinfoservice live.
            val perm = it.permission?.let { p -> " perm=${p.substringAfterLast('.')}" } ?: ""
            emitInfo("service ${it.name.substringAfterLast('.')} exported=${it.exported}$perm")
        }
        info.receivers?.take(20)?.forEach {
            emitInfo("receiver ${it.name.substringAfterLast('.')} exported=${it.exported}")
        }
        info.requestedPermissions
            ?.filter { p -> listOf("can", "car", "mcu", "vehicle", "uart", "serial").any { p.contains(it, true) } }
            ?.takeIf { it.isNotEmpty() }
            ?.let { emitInfo("perms: ${it.joinToString { p -> p.substringAfterLast('.') }}") }
        info.services?.filter { it.exported }?.forEach { svc ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val desc = runCatching { binder?.interfaceDescriptor }.getOrNull()
                    emitInfo("bound ${svc.name.substringAfterLast('.')} → ${desc ?: "no descriptor"}")
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }
            try {
                val accepted = context.bindService(
                    Intent().setComponent(ComponentName(pkg, svc.name)), conn, Context.BIND_AUTO_CREATE,
                )
                emitInfo("bind ${svc.name.substringAfterLast('.')}: ${if (accepted) "accepted" else "refused"}")
                if (!accepted) runCatching { context.unbindService(conn) }
            } catch (e: Exception) {
                emitInfo("bind ${svc.name.substringAfterLast('.')} threw ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        // Package- or service-name fragments that hint at a CAN / vehicle bridge.
        val CAN_KEYWORDS = listOf(
            "canbus", "can", "vehicle", "toolkit", "syu", "microntek",
            "obd", "raise", "hiworld", "hzbhd", "txznet", "hsae", "autochips", "mcu",
        )
    }
}
