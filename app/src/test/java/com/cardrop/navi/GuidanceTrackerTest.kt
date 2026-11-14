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

    private fun fix(km: Double, offNorthMetres: Double = 0.0, second: Int = 0, kmh: Double = 100.0): GpsFix {
        val p = road.pointAt(km)
        return GpsFix(p.lat + offNorthMetres / 111_320.0, p.lng, speedKmh = kmh, bearing = 90.0, timeMillis = second * 1_000L)
    }

    /** A rest area beside the road at [km], 100 m off it (the 영동고속도로 passes its stops 70-100 m away). */
    private fun restAreaAt(km: Double, id: String = "rest") =
        road.pointAt(km).let { Place(id, "휴게소", it.lat + 100 / 111_320.0, it.lng) }

    /** 100 km/h = 27.8 m per one-second reading. */
    private val step = 100.0 / 3600

    @Test
    fun followsProgressAlongTheRoute() {
        val tracker = GuidanceTracker()
        assertEquals(2.0, tracker.update(route, fix(2.0), waypoint = null).km, 0.01)
        val update = tracker.update(route, fix(2.5, offNorthMetres = 15.0), waypoint = null)
        assertEquals(2.5, update.km, 0.01)
        assertFalse(update.offRoute)
    }

    @Test
    fun keepsRequestingAfterThreeOffRouteReadingsUntilNavigatorCanReroute() {
        val tracker = GuidanceTracker()
        tracker.update(route, fix(3.0), waypoint = null)
        val off = (1..5).map { tracker.update(route, fix(3.1, offNorthMetres = 200.0), waypoint = null) }
        assertTrue(off.all { it.offRoute })
        assertEquals(listOf(false, false, true, true, true), off.map { it.needsReroute })
    }

    @Test
    fun drivingPastTheStopIsPassedNotArrived() {
        val tracker = GuidanceTracker()
        val restArea = restAreaAt(5.0)
        val updates = (0..80).map { s -> tracker.update(route, fix(4.0 + s * step, second = s), restArea) }
        assertEquals(listOf(WaypointResult.PASSED), updates.mapNotNull { it.waypoint })
        // Decided only once the car is well past the stop, not while it could still take the exit.
        assertTrue(updates.first { it.waypoint != null }.km > 5.7)
    }

    @Test
    fun aStopFarFromTheRoadIsPassedOnceTheCarDrivesAway() {
        // Its map point is 700 m off the road: the car never comes within 600 m, yet it drives on past it.
        val tracker = GuidanceTracker()
        val far = road.pointAt(5.0).let { Place("far", "휴게소", it.lat + 700 / 111_320.0, it.lng) }
        val updates = (0..200).map { s -> tracker.update(route, fix(4.0 + s * step, second = s), far) }
        assertEquals(listOf(WaypointResult.PASSED), updates.mapNotNull { it.waypoint })
        assertTrue(updates.first { it.waypoint != null }.km > 5.5)
    }

    @Test
    fun aStopAlreadyBehindTheCarIsPassedAfterItDrivesOn() {
        // The vote ended 1.5 km after the car passed the stop: driving on, it gets further away, never closer.
        val tracker = GuidanceTracker()
        val behind = restAreaAt(1.0)
        val updates = (0..120).map { s -> tracker.update(route, fix(2.5 + s * step, second = s), behind) }
        assertEquals(listOf(WaypointResult.PASSED), updates.mapNotNull { it.waypoint })
        assertTrue(updates.first { it.waypoint != null }.km < 3.6) // about 1 km later
    }

    @Test
    fun aStopFarAheadIsNotPassedWhileTheCarApproaches() {
        val tracker = GuidanceTracker()
        val ahead = restAreaAt(9.5)
        val updates = (0..150).map { s -> tracker.update(route, fix(4.0 + s * step, second = s), ahead) }
        assertTrue(updates.none { it.waypoint != null })
    }

    @Test
    fun stoppingAtTheStopForTenSecondsIsArrived() {
        val tracker = GuidanceTracker()
        val restArea = restAreaAt(5.0)
        val approach = (0..20).map { s -> tracker.update(route, fix(4.4 + s * step, second = s), restArea) }
        // Parked in the rest area, 100 m off the road, standing still.
        val parked = (21..40).map { s -> tracker.update(route, fix(5.0, offNorthMetres = 100.0, second = s, kmh = 0.0), restArea) }
        assertTrue(approach.none { it.waypoint != null })
        assertEquals(10, parked.indexOfFirst { it.waypoint == WaypointResult.ARRIVED }) // 10 s after stopping
        assertEquals(1, parked.count { it.waypoint != null })
        assertTrue(parked.none { it.needsReroute }) // off the road beside the stop is not "lost"
    }

    @Test
    fun getsToTheStopOnceBeforeItIsDecided() {
        // Driving past: "at the stop" within 300 m, then PASSED well after it.
        val past = GuidanceTracker().let { t -> (0..80).map { s -> t.update(route, fix(4.0 + s * step, second = s), restAreaAt(5.0)) } }
        assertEquals(1, past.count { it.atWaypoint })
        assertTrue(past.indexOfFirst { it.atWaypoint } < past.indexOfFirst { it.waypoint == WaypointResult.PASSED })
        assertTrue(past.first { it.atWaypoint }.km > 4.7) // about 300 m before the stop, not 600

        // Stopping: "at the stop" first, ARRIVED 10 s of standing still later.
        val tracker = GuidanceTracker()
        val stop = restAreaAt(5.0)
        val updates = (0..20).map { s -> tracker.update(route, fix(4.4 + s * step, second = s), stop) } +
            (21..40).map { s -> tracker.update(route, fix(5.0, offNorthMetres = 100.0, second = s, kmh = 0.0), stop) }
        assertEquals(1, updates.count { it.atWaypoint })
        assertTrue(updates.indexOfFirst { it.atWaypoint } < updates.indexOfFirst { it.waypoint == WaypointResult.ARRIVED })
    }

    @Test
    fun aShortSlowdownBesideTheStopIsNotAnArrival() {
        val tracker = GuidanceTracker()
        val restArea = restAreaAt(5.0)
        var km = 4.5
        var second = 0
        val updates = buildList {
            repeat(15) { add(tracker.update(route, fix(km, second = second++), restArea)); km += step }
            repeat(5) { add(tracker.update(route, fix(km, second = second++, kmh = 10.0), restArea)); km += 0.003 } // traffic
            repeat(60) { add(tracker.update(route, fix(km, second = second++), restArea)); km += step }
        }
        assertEquals(listOf(WaypointResult.PASSED), updates.mapNotNull { it.waypoint })
    }

    @Test
    fun aNewVoteForAStopAlreadyPassedIsWatchedAgain() {
        val tracker = GuidanceTracker()
        val restArea = restAreaAt(5.0)
        (0..80).forEach { s -> tracker.update(route, fix(4.0 + s * step, second = s), restArea) } // passed
        tracker.update(route, fix(6.3, second = 81), waypoint = null) // the navigator dropped it
        // The crew votes for it again and this car comes back and stops there.
        val back = (82..100).map { s -> tracker.update(route, fix(5.0, offNorthMetres = 100.0, second = s, kmh = 0.0), restArea) }
        assertEquals(listOf(WaypointResult.ARRIVED), back.mapNotNull { it.waypoint })
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
