package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** A car's fuel as board 04's crew card shows it: percent (0..100) and range left in whole km (null = not known). */
data class CarFuel(val percent: Int, val rangeKm: Int? = null) {
    /** The card turns orange ("연료 경고"). */
    val isLow: Boolean get() = percent < LOW_FUEL_CARD_PERCENT
}

/** This car's fuel from what it reports; null without a fuel level (or with an impossible one). */
fun VehicleSnapshot.carFuel(): CarFuel? {
    val percent = fuelPercent?.takeIf { it in 0..100 } ?: return null
    return CarFuel(percent, rangeKm?.takeIf { it.isFinite() && it >= 0f }?.roundToInt())
}

/**
 * Owner: lead. The cars' fuel by car id as they shared it, for board 04's crew cards. A value for this car is
 * ignored: its own car says it better ([crewCardFuel]). Real crew: what each device shares from its car
 * ([FirebaseTrip]). Bot crew: [BotFuel].
 */
interface CrewFuel {
    val fuel: StateFlow<Map<String, CarFuel>>

    /** No other car's fuel. */
    object None : CrewFuel {
        override val fuel: StateFlow<Map<String, CarFuel>> = MutableStateFlow(emptyMap())
    }
}

/** The bot crew's fuel: [botFuel] of the crew's drops. */
class BotFuel(scope: CoroutineScope, drops: StateFlow<List<Drop>>) : CrewFuel {
    override val fuel: StateFlow<Map<String, CarFuel>> =
        drops.map(::botFuel).stateIn(scope, SharingStarted.Eagerly, botFuel(drops.value))
}

/**
 * The bots' fuel: the design's numbers (board 04: 삼촌 차 48%, 이모 차 55%), with 민지 차 the lowest. A bot that sent
 * a fuel warning (the DEMO panel's "민지 차 연료 경고 받기") is as low as its warning says: 12% · 85km, as on board 05.
 */
internal fun botFuel(drops: List<Drop>): Map<String, CarFuel> {
    val warned = drops.filter { it.topic == DropTopic.FUEL }.map { it.fromCarId }.toSet()
    return BOT_FUEL.mapValues { (carId, fuel) -> if (carId in warned) BOT_LOW_FUEL else fuel }
}

/**
 * Board 04: the fuel each crew card shows, by car id. This car's is [mine], from its own car. Another car's is
 * the one it shared ([others]), only while it is connected: a closed app leaves its last value behind, and a
 * car that hasn't joined yet has none.
 */
fun crewCardFuel(cars: List<Car>, myCarId: String, mine: CarFuel?, others: Map<String, CarFuel>): Map<String, CarFuel> =
    cars.mapNotNull { car ->
        val fuel = if (car.id == myCarId) mine else others[car.id]?.takeIf { car.connected }
        fuel?.let { car.id to it }
    }.toMap()

/** About 6.5 km per percent, like the fake car (vehicle/VehicleRepository.kt). */
private val BOT_FUEL = mapOf(
    DemoData.uncle.id to CarFuel(48, 312),
    DemoData.minji.id to CarFuel(23, 150),
    DemoData.aunt.id to CarFuel(55, 358),
)
private val BOT_LOW_FUEL = CarFuel(12, 85)
