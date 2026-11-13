package com.cardrop.drive

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import com.cardrop.navi.NavigatorState
import com.cardrop.navi.tripTimeText
import kotlin.math.roundToInt

/** How far a place is from this car by road, and how long the drive takes. */
data class PlaceTrip(val km: Double, val minutes: Double) {
    /** "9km", as the boards write it. */
    val kmText: String get() = placeKmText(km)

    /** "6분", "1시간 5분"; never "0분". */
    val minutesText: String get() = tripTimeText(minutes.roundToInt())
}

/** The crew's average time to a place: [minutes] over [cars] cars (board 10's "12분 · 4대 평균"). */
data class CrewTime(val minutes: Double, val cars: Int) {
    val minutesText: String get() = tripTimeText(minutes.roundToInt())
}

/**
 * Owner: lead. Boards 10 and 11: how far each place is from this car and how long it takes to get there
 * (11's "9km", "6분"), and the crew's average (10's "12분 · 4대 평균").
 *
 * Read along the in-app navigator's live route, so it is the drive by road (Kakao's times include traffic),
 * not a straight line. That works for places near the route: the rest stops on the motorway, the cafés by the
 * destination beach. A place more than 1.5 km off the route (a town off the motorway, another destination) or
 * one this car already passed has no time, and the screens then show none.
 *
 * One of these is a snapshot of the navigator and the crew positions; AppContainer.placeTimes makes a new one on
 * every GPS fix, so a screen that collects it stays up to date. Pure Kotlin, unit tested (PlaceTimesTest).
 */
class PlaceTimes(
    private val state: NavigatorState = NavigatorState(),
    private val positions: Map<String, CarPosition> = emptyMap(),
    private val myCarId: String = "",
) {
    /** This car's way to [place], or null: no route yet, too far off the route, or already passed. */
    fun trip(place: Place): PlaceTrip? {
        val route = state.route ?: return null
        val located = route.geometry.locate(place.lat, place.lng)
        if (located.offMeters > NEAR_ROUTE_METERS) return null
        val km = located.km - state.progressKm
        if (km < -PASSED_KM) return null
        return PlaceTrip(km.coerceAtLeast(0.0), route.secondsBetween(state.progressKm, located.km) / 60)
    }

    /**
     * The average time to [place] of the cars in [carIds] whose time is known. The other cars are timed from this
     * car's way there, as on board 14: a car 2 minutes behind this one gets there 2 minutes later
     * ([CarPosition.etaMinutes]); that works because the crew drives the same road. A car with no position yet,
     * or one already past the place, is left out. Null when this car's own time isn't known.
     */
    fun crewTime(place: Place, carIds: List<String>): CrewTime? {
        val mine = trip(place) ?: return null
        val me = positions[myCarId]
        val times = carIds.mapNotNull { id ->
            val other = positions[id]
            when {
                id == myCarId -> mine.minutes
                me == null || other == null -> null
                mine.km + (other.kmToDestination - me.kmToDestination) < -PASSED_KM -> null
                else -> (mine.minutes + (other.etaMinutes - me.etaMinutes)).coerceAtLeast(0.0)
            }
        }
        return if (times.isEmpty()) null else CrewTime(times.average(), times.size)
    }

    private companion object {
        /** Further from the route than this, a place isn't on the way (the same limit as board 14's arrivals). */
        const val NEAR_ROUTE_METERS = 1_500.0

        /** Further behind than this, the car has passed the place. */
        const val PASSED_KM = 0.3
    }
}

/** "9km" (whole km, like the boards: 10's "9km · 주유 · …", 11's "9km · 가장 가까워요"), "850m" under 1 km. */
fun placeKmText(km: Double): String {
    val metres = (km * 1000).roundToInt()
    return if (metres < 1000) "${metres}m" else "${km.roundToInt()}km"
}
