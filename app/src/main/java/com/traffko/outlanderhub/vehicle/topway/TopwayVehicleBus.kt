package com.traffko.outlanderhub.vehicle.topway

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.traffko.outlanderhub.vehicle.BusEvent
import com.traffko.outlanderhub.vehicle.VehicleBus
import com.traffko.outlanderhub.vehicle.VehicleSource
import com.traffko.outlanderhub.vehicle.VehicleState
import com.traffko.outlanderhub.vehicle.fyt.FytSignalDecoder
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
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
        val gen = ++generation
        opening = true
        scope.launch {
            try {
                establish(gen)
            } finally {
                synchronized(this@TopwayVehicleBus) { opening = false }
            }
        }
    }

    private suspend fun establish(gen: Int) {
        val cls = TwUtilReader.resolveClass(context.classLoader)
        if (cls == null) {
            emitInfo("${TwUtilReader.CLASS_NAME} not on this device — the Topway source only works on the real unit")
            return
        }
        val thread = HandlerThread("tw-can").apply { start() }
        val receiver = Handler(thread.looper) { msg ->
            onMcuMessage(msg.what, msg.arg1, msg.arg2, msg.obj)
            true
        }
        emitInfo("opening TWUtil channel ${TwUtilReader.CANBUS_CHANNEL} with a ${SWEEP_IDS.size}-id discovery sweep …")
        var link = openWithTimeout(cls, SWEEP_IDS, receiver)
        if (link == null) {
            emitInfo("sweep open failed — retrying with the ${CORE_IDS.size} reference ids")
            link = openWithTimeout(cls, CORE_IDS, receiver)
        }
        val opened = link
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
        _state.update { it.copy(connected = true) }
        emitInfo("TWUtil session LIVE — MCU messages appear as tw-can events; drive/press buttons and map what changes")
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
        when (obj) {
            null -> Unit
            is ByteArray -> {
                ints += obj.map { it.toInt() and 0xFF }
                strings += obj.joinToString(" ") { "%02x".format(it) }
            }
            is IntArray -> ints += obj.toList()
            is String -> strings += obj
            else -> strings += obj.toString()
        }
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
        const val OPEN_TIMEOUT_MS = 5000L

        // Discovery sweep: every message id the decompiled reference apps use
        // sits either in the low table (BT 513/769, AUX 517, the factory CAN
        // monitor's 0x0501) or the 0x9E00 control page (BT/AUX writes
        // 40448-40477) — subscribe both wholesale to see what the MCU offers.
        val SWEEP_IDS = ShortArray(0x1000 + 0x100) { i ->
            if (i < 0x1000) i.toShort() else (0x9E00 + (i - 0x1000)).toShort()
        }
        val CORE_IDS = shortArrayOf(0x0501, 513, 517, 769)
    }
}
