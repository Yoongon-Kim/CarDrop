package com.cardrop.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.util.Log
import com.cardrop.core.model.Gear
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Owner: Member B.
 *
 * Reads the car through Pleos Vehicle SDK 2.3.2, following the lifecycle used in
 * pleos-sample-vehicle-app: initialize -> read once -> register listeners -> unregister -> release.
 *
 * Day-1 check: run this on the Pleos emulator and watch Logcat (tag "CarDropVehicle").
 * Each value that fails logs its error, and the snapshot keeps null for it.
 *
 * TODO(Member B): call checkXxxCapabilities() before reading, like the sample does.
 * TODO(Member B): getTripCurrentDrivingTime units are unverified (seconds or minutes?). Check on the
 *  emulator and fix toMinutes() below.
 */
class PleosVehicleRepository(context: Context) : VehicleRepository {

    private val vehicle = Vehicle(context.applicationContext)
    private var started = false

    private val _snapshot = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot

    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(percent: Int?) {
            _snapshot.update { it.copy(fuelPercent = percent) }
        }
        override fun onFailed(exception: Exception) = logFailure("fuel listener", exception)
    }

    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(km: Float?) {
            _snapshot.update { it.copy(rangeKm = km) }
        }
        override fun onFailed(exception: Exception) = logFailure("range listener", exception)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _snapshot.update { it.copy(gear = gear.toGear()) }
        }
        override fun onFailed(exception: Exception) = logFailure("gear listener", exception)
    }

    private val drivingTimeListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(time: Int?) {
            _snapshot.update { it.copy(drivingMinutes = time?.toMinutes()) }
        }
        override fun onFailed(exception: Exception) = logFailure("driving time listener", exception)
    }

    override fun start() {
        if (started) return
        try {
            vehicle.initialize()
            started = true
        } catch (exception: Exception) {
            logFailure("initialize", exception)
            return
        }

        val fuel = vehicle.getFuel()
        val odometer = vehicle.getOdometer()
        val gear = vehicle.getGear()

        // Read once so the UI has values before the first change arrives.
        fuel.getFuelPercent(
            { percent -> _snapshot.update { it.copy(fuelPercent = percent) } },
            { logFailure("getFuelPercent", it) },
        )
        odometer.getRangeRemainingDistance(
            { km -> _snapshot.update { it.copy(rangeKm = km) } },
            { logFailure("getRangeRemainingDistance", it) },
        )
        gear.getCurrentGearSelectionState(
            { value -> _snapshot.update { it.copy(gear = value.toGear()) } },
            { logFailure("getCurrentGearSelectionState", it) },
        )
        odometer.getTripCurrentDrivingTime(
            { time -> _snapshot.update { it.copy(drivingMinutes = time?.toMinutes()) } },
            { logFailure("getTripCurrentDrivingTime", it) },
        )

        runCatching { fuel.registerFuelPercent(fuelListener) }.onFailure { logFailure("registerFuelPercent", it) }
        runCatching { odometer.registerRangeRemainingListener(rangeListener) }
            .onFailure { logFailure("registerRangeRemainingListener", it) }
        runCatching { gear.registerGearSelection(gearListener) }.onFailure { logFailure("registerGearSelection", it) }
        runCatching { odometer.registerTripCurrentDrivingTime(drivingTimeListener) }
            .onFailure { logFailure("registerTripCurrentDrivingTime", it) }
    }

    override fun stop() {
        if (!started) return
        // A listener must never outlive the Vehicle session that registered it.
        runCatching { vehicle.getFuel().unregisterFuelPercent(fuelListener) }
        runCatching { vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener) }
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getOdometer().unregisterTripCurrentDrivingTime(drivingTimeListener) }
        runCatching { vehicle.release() }
        started = false
    }

    private fun VehicleGear?.toGear(): Gear = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN, null -> Gear.UNKNOWN
        // D and the manual gears 1..9 all mean "driving".
        else -> Gear.DRIVE
    }

    /** Assumes the SDK reports seconds. Verify on the emulator (see TODO above). */
    private fun Int.toMinutes(): Int = this / 60

    private fun logFailure(what: String, error: Throwable) {
        Log.w(TAG, "$what failed: ${error.message}", error)
    }

    private companion object {
        const val TAG = "CarDropVehicle"
    }
}
