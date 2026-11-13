package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 01's "약 62km · 1시간 5분" from the navigator's live route. */
class DestinationTripTest {

    /** About 100 km due north, driven in exactly one hour. */
    private val start = GeoPoint(37.0, 128.0)
    private val end = GeoPoint(37.9, 128.0)
    private val geometry = RouteGeometry(listOf(start, GeoPoint(37.45, 128.0), end))
    private val route = LiveRoute(geometry, emptyList(), listOf(geometry.totalKm to 3600.0), null, "test")
    private val destination = Place("dest", "목적지", end.lat, end.lng + 0.005) // ~440 m off the road's end

    @Test
    fun atTheStartItIsTheWholeRoute() {
        val trip = tripToDestination(NavigatorState(route = route), destination)!!
        assertEquals(geometry.totalKm, trip.km, 1e-9)
        assertEquals(60, trip.minutes)
        assertEquals("약 100km · 1시간", trip.text)
    }

    @Test
    fun halfwayItIsTheRestOfTheRoute() {
        val half = geometry.totalKm / 2
        val trip = tripToDestination(NavigatorState(route = route, progressKm = half), destination)!!
        assertEquals(half, trip.km, 1e-9)
        assertEquals(30, trip.minutes)
    }

    @Test
    fun noRouteOrARouteToAnotherPlaceGivesNothing() {
        assertNull(tripToDestination(NavigatorState(), destination))
        // The destination just changed: the old route still ends far from the new one.
        val elsewhere = Place("other", "다른 곳", 37.5, 128.9)
        assertNull(tripToDestination(NavigatorState(route = route), elsewhere))
    }

    @Test
    fun textLooksLikeTheDesign() {
        assertEquals("2시간 40분", tripTimeText(160))
        assertEquals("2시간", tripTimeText(120))
        assertEquals("9분", tripTimeText(9))
        assertEquals("1분", tripTimeText(0))
        assertEquals("230km", tripKmText(230.4))
        assertEquals("5.4km", tripKmText(5.44))
        assertEquals("850m", tripKmText(0.85))
        assertEquals("약 230km · 2시간 40분", DestinationTrip(230.4, 160).text)
    }
}
