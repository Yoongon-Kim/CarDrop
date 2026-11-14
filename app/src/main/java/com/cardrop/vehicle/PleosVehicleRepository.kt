package com.cardrop.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.util.Log
import android.os.SystemClock
import com.cardrop.core.model.Gear
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owner: Member B. One session, with callbacks and subscriptions tied to that session. */
class PleosVehicleRepository(context: Context) : VehicleRepository {
    private val vehicle = Vehicle(context.applicationContext)
    private val lock = Any()
    private var started = false
    private var generation = 0L
    private val unsubscribe = mutableListOf<() -> Unit>()
    private val gearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gearRefresh: Job? = null
    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot

    override fun start() = synchronized(lock) {
        if (started) return@synchronized
        if (!sdkCall("initialize") { vehicle.initialize() }) return@synchronized
        started = true
        val session = ++generation
        _snapshot.value = VehicleSnapshot()

        // Only fuel/time expose capability queries in 2.3.2. Gear/range failures are isolated.
        sdkCall("fuel capabilities") {
            val fuel = vehicle.getFuel()
            fuel.checkFuelPercentCapabilities({ config -> current(session) {
                if (config.accessType.readable()) {
                    val listener = object : FuelPercentListener {
                        override fun onFuelPercentUpdated(percent: Int?) = current(session) {
                            _snapshot.update { it.copy(fuelPercent = VehicleValues.fuel(percent)) }
                        }
                        override fun onFailed(exception: Exception) = current(session) {
                            _snapshot.update { it.copy(fuelPercent = null) }
                            logFailure("fuel updates", exception)
                        }
                    }
                    sdkCall("getFuelPercent") { fuel.getFuelPercent(listener::onFuelPercentUpdated, listener::onFailed) }
                    subscribe("fuel", { fuel.registerFuelPercent(listener) }, { fuel.unregisterFuelPercent(listener) })
                } else Log.w(TAG, "Fuel is not readable: ${config.accessType}")
            } }, { logFailure("fuel capabilities", it) })
        }
        sdkCall("driving time capabilities") {
            val odometer = vehicle.getOdometer()
            odometer.checkTripCurrentDrivingTimeCapabilities({ config -> current(session) {
                if (config.accessType.readable()) {
                    val listener = object : DrivingTimeListener {
                        override fun onDrivingTimeUpdated(time: Int?) = current(session) {
                            _snapshot.update { it.copy(drivingMinutes = VehicleValues.drivingMinutes(time)) }
                        }
                        override fun onFailed(exception: Exception) = current(session) {
                            _snapshot.update { it.copy(drivingMinutes = null) }
                            logFailure("driving time updates", exception)
                        }
                    }
                    sdkCall("getTripCurrentDrivingTime") {
                        odometer.getTripCurrentDrivingTime(listener::onDrivingTimeUpdated, listener::onFailed)
                    }
                    subscribe("driving time", { odometer.registerTripCurrentDrivingTime(listener) },
                        { odometer.unregisterTripCurrentDrivingTime(listener) })
                } else Log.w(TAG, "Driving time is not readable: ${config.accessType}")
            } }, { logFailure("driving time capabilities", it) })
        }
        sdkCall("range") {
            val odometer = vehicle.getOdometer()
            val listener = object : RangeRemainingListener {
                override fun onRangeRemainingChanged(metres: Float?) = current(session) {
                    _snapshot.update { it.copy(rangeKm = VehicleValues.rangeKm(metres)) }
                }
                override fun onFailed(exception: Exception) = current(session) {
                    _snapshot.update { it.copy(rangeKm = null) }
                    logFailure("range updates", exception)
                }
            }
            sdkCall("getRangeRemainingDistance") {
                odometer.getRangeRemainingDistance(listener::onRangeRemainingChanged, listener::onFailed)
            }
            subscribe("range", { odometer.registerRangeRemainingListener(listener) },
                { odometer.unregisterRangeRemainingListener(listener) })
        }
        sdkCall("gear") {
            val gear = vehicle.getGear()
            val reads = GearReadEpoch()
            fun publishGear(value: VehicleGear?) {
                _snapshot.update { it.copy(gear = value.toGear(), gearCheckedAtElapsedMillis = SystemClock.elapsedRealtime()) }
            }
            fun failedGear(exception: Exception) {
                _snapshot.update { it.copy(gear = Gear.UNKNOWN, gearCheckedAtElapsedMillis = null) }
                logFailure("gear updates", exception)
            }
            val listener = object : GearSelectionListener {
                override fun onSelected(value: VehicleGear?) = current(session) {
                    reads.event()
                    publishGear(value)
                }
                override fun onFailed(exception: Exception) = current(session) {
                    reads.event()
                    failedGear(exception)
                }
            }
            fun readGear() {
                val ticket = reads.request()
                val sent = sdkCall("getCurrentGearSelectionState") {
                    gear.getCurrentGearSelectionState(
                        { value -> current(session) { if (reads.complete(ticket)) publishGear(value) } },
                        { exception -> current(session) { if (reads.complete(ticket)) failedGear(exception) } },
                    )
                }
                if (!sent && reads.complete(ticket)) _snapshot.update { it.copy(gear = Gear.UNKNOWN, gearCheckedAtElapsedMillis = null) }
            }
            readGear()
            subscribe("gear", { gear.registerGearSelection(listener) }, { gear.unregisterGearSelection(listener) })
            // Gear callbacks can be change-only. Re-read instead of refreshing an old cached P lease.
            gearRefresh = gearScope.launch {
                while (true) {
                    delay(2_000)
                    current(session) { readGear() }
                }
            }
        }
        Unit
    }

    override fun stop() = synchronized(lock) {
        if (!started) return@synchronized
        started = false
        generation++ // Ignore callbacks from the old session, even after a subsequent start().
        gearRefresh?.cancel()
        gearRefresh = null
        unsubscribe.asReversed().forEach { sdkCall("unregister", it) }
        unsubscribe.clear()
        sdkCall("release") { vehicle.release() }
        _snapshot.value = VehicleSnapshot()
    }

    private fun current(session: Long, action: () -> Unit) = synchronized(lock) {
        if (started && generation == session) action()
    }
    private fun subscribe(name: String, register: () -> Unit, unregister: () -> Unit) {
        if (sdkCall("register $name", register)) unsubscribe += unregister
    }
    private fun sdkCall(name: String, action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: Exception) {
        logFailure(name, error)
        false
    }
    private fun AccessType.readable() = this == AccessType.READ || this == AccessType.READ_WRITE
    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }
    private fun logFailure(what: String, error: Throwable) { Log.w(TAG, "$what failed: ${error.message}", error) }
    private companion object { const val TAG = "CarDropVehicle" }
}
