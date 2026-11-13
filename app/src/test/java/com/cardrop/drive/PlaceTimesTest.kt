package com.cardrop.drive

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import com.cardrop.navi.LiveRoute
import com.cardrop.navi.NavigatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Boards 10 and 11: how far a place is from this car by road, how long it takes, and the crew's average. */
class PlaceTimesTest {

    /** About 100 km due north, driven at 100 km/h (1 km = 36 seconds). */
    private val geometry = RouteGeometry(listOf(GeoPoint(37.0, 128.0), GeoPoint(37.45, 128.0), GeoPoint(37.9, 128.0)))
    private val route = LiveRoute(geometry, emptyList(), listOf(geometry.totalKm to geometry.totalKm * 36), null, "test")
    private val kmPerDegree = geometry.totalKm / 0.9

    /** A place on the road, [km] from the route's start; [eastKm] off it to the east. */
    private fun place(km: Double, eastKm: Double = 0.0) =
        Place("p$km", "휴게소", 37.0 + km / kmPerDegree, 128.0 + eastKm / (kmPerDegree * Math.cos(Math.toRadians(37.0))))

    private val me = "me"
    private val uncle = "uncle"
    private val minji = "minji"
    private val aunt = "aunt"

    // This car is 10 km along the route; the others are 0.8, 1.8 and 3 km behind (1, 2 and 3 minutes later).
    private val positions = mapOf(
        me to CarPosition(me, 0.0, 0.0, 90f, 54),
        uncle to CarPosition(uncle, 0.0, 0.0, 90.8f, 55),
        minji to CarPosition(minji, 0.0, 0.0, 91.8f, 56),
        aunt to CarPosition(aunt, 0.0, 0.0, 93f, 57),
    )
    private val times = PlaceTimes(NavigatorState(route = route, progressKm = 10.0), positions, me)

    @Test
    fun aPlaceAheadOnTheRouteIsTheRoadBetween() {
        val trip = times.trip(place(20.0))!!
        assertEquals(10.0, trip.km, 0.05)
        assertEquals(6.0, trip.minutes, 0.05)
        assertEquals("10km", trip.kmText)
        assertEquals("6분", trip.minutesText)
    }

    @Test
    fun aPlaceBesideTheRoadCountsButOneInTownDoesNot() {
        assertEquals(10.0, times.trip(place(20.0, eastKm = 1.0))!!.km, 0.05) // a rest stop 1 km off the road
        assertNull(times.trip(place(20.0, eastKm = 3.0)))
    }

    @Test
    fun aPlaceThisCarPassedHasNoTime() {
        assertNull(times.trip(place(5.0)))
        assertEquals(0.0, times.trip(place(9.9))!!.km, 1e-9) // right beside it: about to get there
    }

    @Test
    fun withoutARouteNothingIsKnown() {
        assertNull(PlaceTimes(NavigatorState(), positions, me).trip(place(20.0)))
        assertNull(PlaceTimes().crewTime(place(20.0), listOf(me)))
    }

    @Test
    fun theCrewAverageTimesTheOtherCarsFromThisOne() {
        val crew = times.crewTime(place(20.0), listOf(me, uncle, minji, aunt))!!
        assertEquals(4, crew.cars)
        assertEquals(7.5, crew.minutes, 0.05) // 6, 7, 8 and 9 minutes
        assertEquals("7분", CrewTime(7.4, 4).minutesText)
    }

    @Test
    fun onlyTheChosenCarsWithAPositionCount() {
        val crew = PlaceTimes(NavigatorState(route = route, progressKm = 10.0), positions - aunt, me)
            .crewTime(place(20.0), listOf(me, minji, aunt))!!
        assertEquals(2, crew.cars) // 이모 has no position yet; 삼촌 wasn't asked
        assertEquals(7.0, crew.minutes, 0.05)
    }

    @Test
    fun aCarAlreadyPastThePlaceIsLeftOut() {
        // 삼촌 is 12 km ahead of this car, so 2 km past a place 10 km ahead.
        val ahead = positions + (uncle to CarPosition(uncle, 0.0, 0.0, 78f, 47))
        val crew = PlaceTimes(NavigatorState(route = route, progressKm = 10.0), ahead, me).crewTime(place(20.0), listOf(me, uncle))!!
        assertEquals(1, crew.cars)
    }

    @Test
    fun distancesAreWholeKmLikeTheBoards() {
        assertEquals("9km", placeKmText(9.0))
        assertEquals("9km", placeKmText(9.4))
        assertEquals("33km", placeKmText(32.6))
        assertEquals("850m", placeKmText(0.85))
        assertEquals("1km", placeKmText(0.9996))
    }
}
