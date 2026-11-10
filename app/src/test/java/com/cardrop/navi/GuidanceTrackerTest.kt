package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceTrackerTest {

    /** A straight 10 km road heading east along latitude 37.5. */
    private val road = RouteGeometry((0..10).map { GeoPoint(37.5, 127.0 + it * 0.01131) })
    private val route = LiveRoute(road, steps = emptyList(), timeline = emptyList(), waypointKm = null, provider = "test")

    private fun fix(km: Double, offNorthMetres: Double = 0.0, second: Int = 0): GpsFix {
        val p = road.pointAt(km)
        return GpsFix(p.lat + offNorthMetres / 111_320.0, p.lng, speedKmh = 100.0, bearing = 90.0, timeMillis = second * 1_000L)
    }

    @Test
    fun followsProgressAlongTheRoute() {
        val tracker = GuidanceTracker()
        assertEquals(2.0, tracker.update(route, fix(2.0), waypoint = null).km, 0.01)
        val update = tracker.update(route, fix(2.5, offNorthMetres = 15.0), waypoint = null)
        assertEquals(2.5, update.km, 0.01)
        assertFalse(update.offRoute)
    }

    @Test
    fun asksForANewRouteOnceAfterThreeOffRouteReadings() {
        val tracker = GuidanceTracker()
        tracker.update(route, fix(3.0), waypoint = null)
        val off = (1..5).map { tracker.update(route, fix(3.1, offNorthMetres = 200.0), waypoint = null) }
        assertTrue(off.all { it.offRoute })
        assertEquals(listOf(false, false, true, false, false), off.map { it.needsReroute })
    }

    @Test
    fun reachingTheWaypointNeedsNoReroute() {
        val tracker = GuidanceTracker()
        val restArea = road.pointAt(5.0).let { Place("rest", "휴게소", it.lat + 200 / 111_320.0, it.lng) }
        tracker.update(route, fix(4.0), restArea)
        assertFalse(tracker.update(route, fix(4.0), restArea).reachedWaypoint)
        val update = tracker.update(route, fix(5.0), restArea)
        assertTrue(update.reachedWaypoint)
    }

    @Test
    fun arrivesAtTheEnd() {
        val tracker = GuidanceTracker()
        tracker.update(route, fix(9.0), waypoint = null)
        assertTrue(tracker.update(route, fix(road.totalKm), waypoint = null).arrived)
    }

    @Test
    fun fillsInSpeedAndDirectionWhenTheGpsLeavesThemOut() {
        val tracker = GuidanceTracker()
        tracker.withMotion(fix(1.0, second = 0).copy(speedKmh = null, bearing = null))
        val second = tracker.withMotion(fix(1.1, second = 4).copy(speedKmh = null, bearing = null)) // 100 m in 4 s
        assertEquals(90.0, second.speedKmh!!, 2.0)
        assertEquals(90.0, second.bearing!!, 1.0)
    }
}
