package com.traffko.outlanderhub.vehicle

import android.content.Context
import android.os.SystemClock
import com.traffko.outlanderhub.vehicle.fyt.FytVehicleBus
import com.traffko.outlanderhub.vehicle.fyt.SignalKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Owns the active [VehicleBus], allows hot-switching between the demo source
 * and the real FYT CAN source, and keeps a bounded log of raw bus events for
 * the Diagnostics screen.
 */
class VehicleRepository(
    private val context: Context,
    signalMap: StateFlow<Map<Int, SignalKind>>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val demoBus = DemoVehicleBus()
    private val fytBus = FytVehicleBus(context, signalMap)

    private var activeBus: VehicleBus = demoBus
    private var pumpJobs = mutableListOf<Job>()

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    // Raw events arrive at CAN-bus rates, so they are buffered here and the
    // public snapshot is published at most every LOG_PUBLISH_MS — copying the
    // full list per event would churn allocations and recompose the
    // diagnostics list for every frame on the bus.
    private val logLock = Any()
    private val logBuffer = ArrayDeque<BusEvent>()
    private var logGeneration = 0L
    private val logDirty = Channel<Unit>(Channel.CONFLATED)
    // Last payload signature per code, for stamping BusEvent.changed.
    private val lastPayload = HashMap<Int, Int>()

    // Monotonic (elapsedRealtime) — wall time jumps when the unit syncs its
    // clock from GPS/NTP after boot, which would corrupt silence intervals.
    @Volatile private var lastEventAtMs = 0L

    private val _eventLog = MutableStateFlow(EventLog())
    val eventLog: StateFlow<EventLog> = _eventLog.asStateFlow()

    private val _activeSource = MutableStateFlow(VehicleSource.DEMO)
    val activeSource: StateFlow<VehicleSource> = _activeSource.asStateFlow()

    fun setSource(source: VehicleSource) {
        if (source == _activeSource.value && pumpJobs.isNotEmpty()) return
        stopPumps()
        activeBus.stop()
        activeBus = when (source) {
            VehicleSource.DEMO -> demoBus
            VehicleSource.FYT_CAN -> fytBus
        }
        _activeSource.value = source
        activeBus.start()
        startPumps(activeBus)
    }

    /**
     * Called from the process lifecycle. The FYT binding deliberately stays
     * alive in the background (so door/body alerts are current the moment the
     * user returns), but the demo simulation has no reason to burn CPU while
     * another app is foreground.
     */
    fun setAppVisible(visible: Boolean) {
        if (visible) {
            activeBus.start()
        } else if (activeBus === demoBus) {
            demoBus.stop()
        }
    }

    fun sendCommand(code: Int, ints: IntArray = intArrayOf()): Boolean =
        activeBus.sendCommand(code, ints)

    /** One-shot get() probe of the FYT static config codes; no-op on other sources. */
    fun probeFytConfig() {
        if (activeBus === fytBus) fytBus.probeConfig()
    }

    fun clearEventLog() {
        synchronized(logLock) {
            logBuffer.clear()
            logGeneration++
        }
        publishLog()
    }

    private fun startPumps(bus: VehicleBus) {
        pumpJobs += scope.launch {
            bus.state.collect { _state.value = it }
        }
        pumpJobs += scope.launch {
            bus.events.collect { event ->
                lastEventAtMs = SystemClock.elapsedRealtime()
                appendToLog(event)
            }
        }
        pumpJobs += scope.launch {
            // Publishes immediately on the first event after a quiet spell,
            // then at most every LOG_PUBLISH_MS while the bus is chatty.
            for (dirty in logDirty) {
                publishLog()
                delay(LOG_PUBLISH_MS)
            }
        }
        if (bus === fytBus) {
            pumpJobs += scope.launch { watchdogLoop(bus) }
        }
    }

    /**
     * The FYT toolkit is an undocumented service on cheap hardware — it can
     * silently stop delivering callbacks while the binding looks healthy.
     * If the bus claims to be connected but no event has arrived for
     * [WATCHDOG_STALE_MS], rebind and say so in the log.
     */
    private suspend fun watchdogLoop(bus: VehicleBus) {
        lastEventAtMs = SystemClock.elapsedRealtime()
        while (true) {
            delay(WATCHDOG_PERIOD_MS)
            if (!_state.value.connected) {
                // Not bound (car off, service gone): silence is expected.
                // Keep the timer fresh so a reconnect isn't instantly judged
                // stale before it has had a chance to deliver anything.
                lastEventAtMs = SystemClock.elapsedRealtime()
                continue
            }
            val silenceMs = SystemClock.elapsedRealtime() - lastEventAtMs
            if (silenceMs > WATCHDOG_STALE_MS) {
                appendToLog(
                    BusEvent(
                        timestampMs = System.currentTimeMillis(),
                        channel = "watchdog-info",
                        code = -1,
                        strings = listOf("no CAN traffic for ${silenceMs / 1000}s — rebinding"),
                    )
                )
                bus.stop()
                bus.start()
                lastEventAtMs = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun appendToLog(event: BusEvent) {
        synchronized(logLock) {
            logBuffer.addLast(stampChanged(event))
            if (logBuffer.size > MAX_LOG) logBuffer.removeFirst()
            logGeneration++
        }
        logDirty.trySend(Unit)
    }

    private fun publishLog() {
        _eventLog.value = synchronized(logLock) { EventLog(logBuffer.toList(), logGeneration) }
    }

    /** Must be called under [logLock]. Info events (code < 0) are never "changed". */
    private fun stampChanged(event: BusEvent): BusEvent {
        if (event.code < 0) return event
        val signature = (event.ints.hashCode() * 31 + event.floats.hashCode()) * 31 + event.strings.hashCode()
        val changed = lastPayload.put(event.code, signature).let { it != null && it != signature }
        return if (changed) event.copy(changed = true) else event
    }

    /**
     * Writes the current log to an app-external file (no permissions needed;
     * reachable over USB/file manager under Android/data). Returns the path,
     * or null on failure.
     */
    suspend fun exportLog(): String? = withContext(Dispatchers.IO) {
        val snapshot = synchronized(logLock) { logBuffer.toList() }
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "can-logs").apply { mkdirs() }
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(Instant.now().atZone(ZoneId.systemDefault()))
            val file = File(dir, "can-log-$stamp.txt")
            file.bufferedWriter().use { out ->
                snapshot.forEach { event ->
                    out.write("${event.timestampMs} ${event.pretty()}")
                    out.newLine()
                }
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun stopPumps() {
        pumpJobs.forEach { it.cancel() }
        pumpJobs.clear()
    }

    private companion object {
        const val MAX_LOG = 400
        const val LOG_PUBLISH_MS = 100L
        const val WATCHDOG_PERIOD_MS = 30_000L
        const val WATCHDOG_STALE_MS = 120_000L
    }
}
