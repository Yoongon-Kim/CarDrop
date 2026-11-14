package com.cardrop.safety

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.cardrop.core.model.Gear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class SafetySourceState { WAITING, CONNECTING, READY, PERMISSION_DENIED, UNAVAILABLE }

/** Only public AAOS speed reads. Privileged ADAS, steering and lights are intentionally not requested. */
class AaosSafetyTelemetry(private val context: Context, private val scope: CoroutineScope) {
    val readings = MutableSharedFlow<SpeedReading>(extraBufferCapacity = 128)
    val state = MutableStateFlow(SafetySourceState.WAITING)
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var setup: Job? = null
    @Volatile private var running = false
    @Volatile private var generation = 0

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (!running || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED || value.status != CarPropertyValue.STATUS_AVAILABLE) return
            val speed = (value.value as? Number)?.toDouble() ?: return
            val reading = SpeedReading(speed, value.timestamp, SystemClock.elapsedRealtimeNanos())
            if (!readings.tryEmit(reading)) Log.w(TAG, "Speed buffer full; detector will treat missing measurements as a gap")
        }
        override fun onErrorEvent(propId: Int, zone: Int) { state.value = SafetySourceState.UNAVAILABLE }
    }

    fun start() {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) { stop(); state.value = SafetySourceState.PERMISSION_DENIED; return }
        if (running) return
        stop() // Also release a previous failed connection before retrying.
        running = true; state.value = SafetySourceState.CONNECTING
        val session = ++generation
        setup = scope.launch(Dispatchers.IO) {
            var client: Car? = null
            var properties: CarPropertyManager? = null
            try {
                client = Car.createCar(context) ?: error("Car service unavailable")
                properties = client.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                val config = properties.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) ?: error("Speed unsupported")
                @Suppress("DEPRECATION")
                val registered = properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f.coerceAtMost(config.maxSampleRate.coerceAtLeast(1f)))
                if (!registered) error("Speed subscription refused")
                withContext(Dispatchers.Main.immediate) {
                    if (running && session == generation) { car = client; manager = properties; state.value = SafetySourceState.READY }
                    else { runCatching { properties.unregisterCallback(callback) }; client.disconnect() }
                }
            } catch (e: Exception) {
                runCatching { properties?.unregisterCallback(callback) }; runCatching { client?.disconnect() }
                if (session == generation) { state.value = SafetySourceState.UNAVAILABLE; running = false }
                Log.w(TAG, "Vehicle speed unavailable", e)
            }
        }
    }

    fun stop() {
        running = false; generation++; setup?.cancel(); setup = null
        runCatching { manager?.unregisterCallback(callback) }; manager = null
        runCatching { car?.disconnect() }; car = null
    }

    companion object { const val PERMISSION = "android.car.permission.CAR_SPEED"; private const val TAG = "CarDropSafety" }
}

/** Real-time synthetic speed traces feed the very same detector as vehicle readings. */
class DemoSafetyTelemetry(private val scope: CoroutineScope, private val gear: () -> Gear) {
    val readings = MutableSharedFlow<SpeedReading>(extraBufferCapacity = 128)
    @Volatile var paused: Boolean = false
    private var job: Job? = null
    private var speed = 22.0
    private data class Command(val type: SafetyEventType, val untilMillis: Long)
    private val command = MutableStateFlow<Command?>(null)
    fun scenario(type: SafetyEventType) {
        command.value = Command(type, SystemClock.elapsedRealtime() + 2_000)
        Log.d("CarDropSafety", "Demo trace requested: $type")
    }
    fun start() {
        if (job?.isActive == true) return
        // Drawing maps/Compose must not starve a short simulated braking/acceleration trace.
        job = scope.launch(Dispatchers.Default) {
            var before = SystemClock.elapsedRealtimeNanos()
            while (true) {
                delay(100)
                val now = SystemClock.elapsedRealtimeNanos()
                val dt = ((now - before) / 1e9).coerceAtMost(.35); before = now
                if (paused) continue
                val scenario = command.value?.takeIf { SystemClock.elapsedRealtime() < it.untilMillis }?.type
                val parked = gear() == Gear.PARK || gear() == Gear.REVERSE
                val target = 22.0
                speed = when {
                    parked -> 0.0
                    scenario == SafetyEventType.ACCELERATION -> speed + 3.4 * dt
                    scenario == SafetyEventType.BRAKING -> (speed - 4.0 * dt).coerceAtLeast(0.0)
                    abs(speed - target) < 1.2 * dt -> target
                    speed < target -> speed + 1.2 * dt
                    else -> speed - 1.2 * dt
                }
                readings.emit(SpeedReading(speed, now, now))
            }
        }
    }
    fun stop() { job?.cancel(); job = null; command.value = null }
}
