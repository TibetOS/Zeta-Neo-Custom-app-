package com.traffko.outlanderhub.vehicle.topway

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.traffko.outlanderhub.diag.CrashReporter
import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.VehicleBus
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import com.traffko.outlanderhub.vehicle.fyt.FytSignalDecoder
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
import com.traffko.outlanderhub.vehicle.fyt.TwUtilInspector
import com.traffko.outlanderhub.vehicle.fyt.TwUtilReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live vehicle data from the Topway MCU serial mux (`android.tw.john.TWUtil`)
 * — the ZETA NEO 14's real CAN surface, confirmed LIVE in-car 2026-07-08.
 *
 * The unit ships no vehicle APK to copy message ids from, so this bus is a
 * discovery instrument first: it subscribes a broad id sweep and mirrors every
 * MCU message into [events] as a `tw-can` line the CAN tab can observe and map
 * to signals, exactly like the FYT path. Decoding shares [FytSignalDecoder]
 * and the live code→signal map.
 */
class TopwayVehicleBus(
    private val context: Context,
    private val signalMap: StateFlow<Map<Int, SignalKind>>,
) : VehicleBus {

    private val _state = MutableStateFlow(VehicleState(source = VehicleSource.TOPWAY_TW))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    // replay: start() narrates the session bring-up before the repository's
    // collector attaches on a source switch — same rationale as FytVehicleBus.
    private val _events = MutableSharedFlow<BusEvent>(replay = 128, extraBufferCapacity = 512)
    override val events: SharedFlow<BusEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var session: TwUtilLink? = null
    private var receiverThread: HandlerThread? = null
    private var opening = false
    // Bumped on every start/stop so a slow open() can detect it was cancelled
    // and tear its freshly-opened session back down instead of leaking it.
    private var generation = 0

    override fun start(): Unit = synchronized(this) {
        if (session != null || opening) return
        // Crash-loop guard: if a previous bring-up armed the latch and never
        // reached LIVE, it aborted the process (very likely a native SIGABRT in
        // the vendor library). Refuse to auto-retry — that would loop the crash
        // on every launch — and tell the user to pick another source. A manual
        // re-select clears the latch via [rearm] so they can force one attempt.
        if (CrashReporter.isArmed()) {
            emitInfo(
                "Topway MCU bring-up crashed the app last time before it went live. " +
                    "Auto-start is disabled to stop the crash loop — the MCU serial link " +
                    "aborts natively on this unit. Switch to Demo, or re-select Topway MCU " +
                    "to force one more attempt (it will be captured)."
            )
            return
        }
        val gen = ++generation
        opening = true
        CrashReporter.arm()
        scope.launch {
            try {
                establish(gen)
            } finally {
                // establish() returned — the process survived this bring-up, so
                // the latch has done its job. (A native abort never reaches here,
                // which is exactly why the latch stays set and blocks next time.)
                CrashReporter.disarm()
                synchronized(this@TopwayVehicleBus) { opening = false }
            }
        }
    }

    /** Clear the crash-loop latch so a deliberate user re-select gets one attempt. */
    fun rearm() = CrashReporter.disarm()

    private suspend fun establish(gen: Int) {
        val cls = TwUtilReader.resolveClass(context.classLoader)
        if (cls == null) {
            emitInfo("${TwUtilReader.CLASS_NAME} not on this device — the Topway source only works on the real unit")
            return
        }
        if (SAFE_MODE) {
            // Until the real TWUtil signature is confirmed, do NOT invoke any
            // vendor method: open()/the (int) ctor abort the process natively on
            // this unit. Dump the API surface (reflection only) so we can read
            // the exact call shape off the firmware, then stop cleanly.
            emitInfo("Topway SAFE MODE: dumping TWUtil API surface instead of opening the serial link (open() aborts natively here).")
            TwUtilInspector.dump(cls).forEach(::emitInfo)
            emitInfo("── end TWUtil surface — share this so the open()/write() call shape can be fixed ──")
            CrashReporter.disarm()
            CrashReporter.breadcrumb("")
            return
        }
        val thread = HandlerThread("tw-can").apply { start() }
        val receiver = Handler(thread.looper) { msg ->
            // Runs on the HandlerThread; an uncaught throw here (vendor msg.obj
            // types are unknown) would take the whole process down, so contain it.
            runCatching { onMcuMessage(msg.what, msg.arg1, msg.arg2, msg.obj) }
                .onFailure { emitInfo("dropped MCU msg what=${msg.what}: ${it.javaClass.simpleName}") }
            true
        }
        // Dump the API surface first (reflection only, cannot crash) so the
        // exact firmware signature is always in the log, then open for real.
        TwUtilInspector.dump(cls).forEach(::emitInfo)
        emitInfo("opening TWUtil with the ${OPEN_IDS.size}-id curated subscription set …")
        val opened = openWithTimeout(cls, OPEN_IDS, receiver)
        val accepted = synchronized(this) {
            when {
                opened == null -> false
                generation != gen -> {
                    opened.close {}
                    false
                }
                else -> {
                    session = opened
                    receiverThread = thread
                    true
                }
            }
        }
        if (!accepted) {
            thread.quitSafely()
            return
        }
        // Past every vendor call without aborting — clear the crash-loop latch
        // and breadcrumb so neither masquerades as a crash on the next launch.
        CrashReporter.disarm()
        CrashReporter.breadcrumb("")
        _state.update { it.copy(connected = true) }
        emitInfo("TWUtil session LIVE — MCU messages appear as tw-can events; drive/press buttons and map what changes")
        val rc = opened?.write(CAN_STATUS_ID, intArrayOf(STATUS_POLL))
        emitInfo("CAN status poll write($CAN_STATUS_ID, $STATUS_POLL) rc=${rc ?: "no overload"}")
    }

    private suspend fun openWithTimeout(cls: Class<*>, ids: ShortArray, receiver: Any): TwUtilLink? =
        withTimeoutOrNull(OPEN_TIMEOUT_MS) {
            runInterruptible {
                TwUtilLink.open(cls, TwUtilReader.CANBUS_CHANNEL, ids, TwUtilReader.SERIAL_BAUD, receiver, ::emitInfo)
            }
        } ?: run {
            emitInfo("TWUtil open timed out after ${OPEN_TIMEOUT_MS}ms — serial held?")
            null
        }

    override fun stop(): Unit = synchronized(this) {
        generation++
        val link = session ?: return
        session = null
        val thread = receiverThread
        receiverThread = null
        scope.launch {
            link.close(::emitInfo)
            thread?.quitSafely()
        }
        _state.update { it.copy(connected = false) }
    }

    override fun sendCommand(code: Int, ints: IntArray, floats: FloatArray, strings: Array<String>): Boolean {
        val link = session ?: return false
        val rc = link.write(code, ints)
        emitInfo(
            if (rc != null) "write($code, ${ints.toList()}) rc=$rc"
            else "write($code): no matching TWUtil.write overload"
        )
        return rc != null && rc >= 0
    }

    private fun onMcuMessage(what: Int, arg1: Int, arg2: Int, obj: Any?) {
        val ints = mutableListOf(arg1, arg2)
        val strings = mutableListOf<String>()
        TwUtilLink.decodePayload(obj, ints, strings)
        val event = BusEvent(
            timestampMs = System.currentTimeMillis(),
            channel = "tw-can",
            code = what,
            ints = ints,
            strings = strings,
        )
        _events.tryEmit(event)
        _state.update { FytSignalDecoder.apply(it, event, signalMap.value) }
    }

    private fun emitInfo(message: String) {
        // A native SIGABRT in TWUtil can kill the process before this async
        // event reaches the log, so persist the bring-up steps synchronously —
        // that file is what surfaces the crashing step on the next launch.
        if (message.startsWith("step: ")) CrashReporter.breadcrumb(message.removePrefix("step: "))
        _events.tryEmit(
            BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "tw-info",
                code = -1,
                strings = listOf(message),
            )
        )
    }

    private companion object {
        // Belt-and-suspenders: when true the bus only dumps TWUtil's API surface
        // and invokes nothing. Now false — the GitHub client survey gave us the
        // safe call shape (no-arg ctor + a small curated id set + open(ids, 0)),
        // so we open for real. The CrashReporter latch still guards against a
        // loop if this unit's firmware differs. Flip back to true to re-inspect.
        const val SAFE_MODE = false

        const val OPEN_TIMEOUT_MS = 5000L

        // "Send me current state" idiom shared by every reference client
        // (volume 515/255, audio focus 769/255, Orbit's STATUS_POLL) — poking
        // the CAN id with it is what the factory monitor's GET 0501 does.
        const val CAN_STATUS_ID = 0x0501
        const val STATUS_POLL = 255

        // Curated subscription set. THE 5120-id sweep we opened with before was
        // the native SIGABRT: every working GitHub client (bphillips09/Orbit,
        // asb72/dvd-bt, d51x/KaierUtils) opens with a handful of ids — the
        // native layer copies them into a fixed-size table and a huge array
        // overflows it. This is KaierUtils' proven `twutil_contexts` set plus
        // the CAN/vehicle id (0x0501) and AUX status (517) we actually want.
        val OPEN_IDS = shortArrayOf(
            0x0501,           // CAN / vehicle (factory monitor's GET 0501)
            513,              // key press
            514,              // sleep/wake
            515,              // volume
            517,              // AUX status
            769,              // audio focus
            33281.toShort(),  // app launch
            40720.toShort(),  // shutdown
            40732.toShort(),  // reverse activity
        )
    }
}
