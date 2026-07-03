package com.traffko.outlanderhub.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * Simulates a drive so the whole UI can be exercised on a desk (or in the
 * Android emulator) without the car. Selected via Settings -> Data source.
 */
class DemoVehicleBus : VehicleBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(VehicleState(source = VehicleSource.DEMO))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BusEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<BusEvent> = _events.asSharedFlow()

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var t = 0.0
            while (true) {
                t += 0.1
                val speed = (60 + 55 * sin(t / 8)).coerceAtLeast(0.0).toFloat()
                val rpm = (900 + speed * 38 + 250 * sin(t * 2)).toInt()
                _state.value = VehicleState(
                    source = VehicleSource.DEMO,
                    connected = true,
                    speedKmh = speed,
                    rpm = rpm,
                    coolantTempC = (88 + 4 * sin(t / 20)).toFloat(),
                    batteryVolts = (14.1 + 0.2 * sin(t / 5)).toFloat(),
                    fuelPercent = (65 - t / 40).coerceAtLeast(5.0).toFloat(),
                    outsideTempC = 27.5f,
                    odometerKm = 84_320,
                    gear = if (speed < 1f) "P" else "D",
                    handbrake = speed < 1f,
                    seatbeltDriver = true,
                    doors = DoorState(),
                    climate = ClimateState(
                        acOn = true,
                        tempLeftC = 22.0f,
                        tempRightC = 22.5f,
                        fanSpeed = 3,
                        recirculating = false,
                    ),
                    tirePressuresKpa = listOf(230f, 231f, 228f, 229f),
                )
                if (abs(t % 2.0) < 0.1) {
                    _events.tryEmit(
                        BusEvent(
                            timestampMs = System.currentTimeMillis(),
                            channel = "demo",
                            code = 1001,
                            ints = listOf(speed.toInt(), rpm),
                        )
                    )
                }
                delay(100)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = VehicleState(source = VehicleSource.DEMO, connected = false)
    }

    override fun sendCommand(code: Int, ints: IntArray, floats: FloatArray, strings: Array<String>): Boolean {
        _events.tryEmit(
            BusEvent(
                timestampMs = System.currentTimeMillis(),
                channel = "demo-cmd",
                code = code,
                ints = ints.toList(),
                floats = floats.toList(),
                strings = strings.toList(),
            )
        )
        return true
    }
}
