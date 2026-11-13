package com.cardrop.vehicle

import com.cardrop.core.model.Gear
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Owner: Member B.
 *
 * What CarDrop reads from this car: fuel %, range left, gear (P/D) and continuous driving time.
 * Switch between fake and real with `cardrop.useRealVehicle` in local.properties.
 */
interface VehicleRepository {
    val snapshot: StateFlow<VehicleSnapshot>

    /** Starts listening to the car. Safe to call more than once. */
    fun start()

    /** Stops listening and releases the SDK session. */
    fun stop()
}

/** Works on any emulator. The demo panel changes its values live. */
class FakeVehicleRepository : VehicleRepository {

    private val _snapshot = MutableStateFlow(
        VehicleSnapshot(fuelPercent = 64, rangeKm = 420f, gear = Gear.DRIVE, drivingMinutes = 35),
    )
    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot

    override fun start() = Unit
    override fun stop() = Unit

    fun setFuelPercent(percent: Int) {
        // Roughly 6.5 km per percent, so 12% shows about 85 km like board 05.
        val value = percent.coerceIn(0, 100)
        _snapshot.update { it.copy(fuelPercent = value, rangeKm = value * 6.5f) }
    }

    fun setGear(gear: Gear) {
        _snapshot.update { it.copy(gear = gear) }
    }
}
