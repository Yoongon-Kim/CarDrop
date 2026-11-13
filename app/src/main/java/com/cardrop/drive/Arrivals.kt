package com.cardrop.drive

import com.cardrop.core.model.Car
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.navi.clock
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** One row of board 14's "도착 예정": a car, what it is doing, and when it gets to the stop. */
data class Arrival(val car: Car, val status: String, val time: String, val stops: Boolean, val minutes: Double?)

/**
 * Board 14 "도착 예정": when each car reaches the decided place.
 *
 * [myKm] and [myMinutes] are this car's distance and time to the place (null = not known yet). Other cars are
 * placed by how far behind this car they are ([CarPosition.kmToDestination], [CarPosition.etaMinutes]); that works
 * because in the demo every car drives the same road. A car that doesn't stop (it didn't join a fuel stop, turned
 * the stop down, or wasn't in the vote) drives on and meets the crew at [destinationName].
 * Cars that stop come first, earliest arrival first.
 */
fun crewArrivals(
    drop: Drop,
    cars: List<Car>,
    positions: Map<String, CarPosition>,
    myCarId: String,
    mySkipped: Boolean,
    myKm: Double?,
    myMinutes: Double?,
    destinationName: String,
    nowMillis: Long,
    zone: ZoneId,
): List<Arrival> {
    val me = positions[myCarId]
    val rows = cars.map { car ->
        val stops = when {
            car.id == myCarId && mySkipped -> false
            !drop.isFor(car.id) -> false
            drop.topic == DropTopic.FUEL -> car.id == drop.fromCarId || car.id in drop.votes
            else -> true
        }
        val position = positions[car.id]
        when {
            !stops -> Arrival(car, "패스 · ${destinationName}에서 합류", "계속", stops = false, minutes = null)
            position == null || me == null || myKm == null || myMinutes == null ->
                Arrival(car, "위치 기다리는 중", "", stops = true, minutes = null)
            else -> {
                val km = myKm + (position.kmToDestination - me.kmToDestination)
                val minutes = myMinutes + (position.etaMinutes - me.etaMinutes)
                if (km <= ARRIVED_KM) Arrival(car, "도착했어요", "—", stops = true, minutes = 0.0)
                else Arrival(car, distanceText(km), clock(nowMillis, minutes * 60, zone), stops = true, minutes = minutes)
            }
        }
    }
    return rows.sortedWith(compareBy<Arrival>({ !it.stops }, { it.minutes ?: Double.MAX_VALUE }))
}

/** "9.0km" under 10 km, then whole km ("15km"), like the design. */
private fun distanceText(km: Double): String = if (km < 10) String.format(Locale.US, "%.1fkm", km) else "${km.roundToInt()}km"

/** Closer than this to the stop counts as there. */
private const val ARRIVED_KM = 0.3
