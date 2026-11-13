package com.cardrop.navi

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteGeometry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class NavigationFlowTest {
    private val geometry = RouteGeometry(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.2)))
    private val road = LiveRoute(geometry, emptyList(), emptyList(), null, "test")
    private fun fix(km: Double, now: Long, offset: Double = 0.0) = geometry.pointAt(km).let {
        GpsFix(it.lat + offset, it.lng, 80.0, 90.0, now)
    }
    private class Location : LocationSource {
        override val fix = MutableStateFlow<GpsFix?>(null)
        override fun start() = true
        override fun stop() = Unit
    }
    private class Router(val result: LiveRoute) : RouteProvider {
        var requests = 0
        override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute {
            requests++
            return result
        }
    }
    @Test fun waitsForGpsAndOnlyStartsGuidanceWithADestination() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val location = Location()
            val router = Router(road)
            val nav = InAppNavigation(scope, location, router) { 100_000 }
            assertFalse(nav.launch())
            assertFalse(nav.guiding.value)
            nav.setRoute(DemoData.gyeongpo)
            assertEquals(NavStatus.WAITING_GPS, nav.state.value.status)
            location.fix.value = fix(0.0, 100_000)
            yield()
            assertEquals(1, router.requests)
            assertEquals(NavStatus.GUIDING, nav.state.value.status)
            assertTrue(nav.launch())
            nav.clear()
            assertNull(nav.route.value.destination)
            assertNull(nav.state.value.route)
            assertFalse(nav.guiding.value)
        } finally { scope.cancel() }
    }
    @Test fun offRouteTriggerSurvivesTheRerouteCooldown() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 100_000L
            val location = Location()
            val router = Router(road)
            val nav = InAppNavigation(scope, location, router) { now }
            location.fix.value = fix(0.0, now)
            nav.setRoute(DemoData.gyeongpo)
            yield()
            for (second in 1..3) {
                now = 100_000L + second * 1_000
                location.fix.value = fix(.1, now, .004)
                yield()
            }
            assertEquals(1, router.requests) // The third bad fix is inside the 8-second cooldown.
            now = 110_000L
            location.fix.value = fix(.1, now, .004)
            yield()
            assertEquals(2, router.requests)
        } finally { scope.cancel() }
    }
    @Test fun theSameMessageForAnotherDestinationIsANewToast() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val nav = InAppNavigation(scope, Location(), Router(road)) { 100_000 }
            // The host picks a destination, then another one: the same text twice.
            nav.setRoute(DemoData.gyeongpo, reason = "크루 목적지를 내비에 넣었어요")
            val first = nav.toast.value
            nav.setRoute(DemoData.destinations.last(), reason = "크루 목적지를 내비에 넣었어요")
            val second = nav.toast.value
            assertEquals("크루 목적지를 내비에 넣었어요", first?.text)
            assertEquals(first?.text, second?.text)
            assertNotEquals(first?.id, second?.id)
            // The trip ends: no toast left over.
            nav.clear()
            assertNull(nav.toast.value)
        } finally { scope.cancel() }
    }
    @Test fun aSecondRerouteInARowIsANewToast() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 100_000L
            val location = Location()
            val router = Router(road)
            val nav = InAppNavigation(scope, location, router) { now }
            location.fix.value = fix(0.0, now)
            nav.setRoute(DemoData.gyeongpo)
            yield()
            val reroutes = (1..2).map {
                // 3 fixes off the route, then one after the 8-second gap between route requests: a new route.
                for (wait in listOf(1_000L, 1_000L, 1_000L, 7_000L)) {
                    now += wait
                    location.fix.value = fix(.1, now, .004)
                    yield()
                }
                nav.route.value to nav.toast.value
            }
            assertEquals(3, router.requests)
            val (routeAfterFirst, firstToast) = reroutes[0]
            val (routeAfterSecond, secondToast) = reroutes[1]
            // The route value is the same after both, so it can't tell the second reroute apart; the toast can.
            assertEquals(routeAfterFirst, routeAfterSecond)
            assertEquals("경로를 벗어나서 새 경로를 찾았어요", firstToast?.text)
            assertEquals(firstToast?.text, secondToast?.text)
            assertNotEquals(firstToast?.id, secondToast?.id)
        } finally { scope.cancel() }
    }
    @Test fun drivingPastTheStopTakesItOffTheRouteAndTellsListeners() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = 100_000L
            val location = Location()
            val router = Router(road)
            val nav = InAppNavigation(scope, location, router) { now }
            val events = mutableListOf<WaypointEvent>()
            scope.launch { nav.waypointEvents.collect { events += it } }
            val reached = mutableListOf<Place>()
            val toasts = mutableListOf<String?>()
            scope.launch { nav.reachedWaypoint.collect { reached += it } }
            scope.launch { nav.route.collect { toasts += it.lastAction } }
            val stop = geometry.pointAt(5.0).let { Place("yeoju", "여주휴게소", it.lat + 100 / 111_320.0, it.lng) }
            location.fix.value = fix(4.0, now)
            nav.setRoute(DemoData.gyeongpo, waypoint = stop)
            yield()
            assertEquals(1, router.requests)
            var km = 4.0
            while (km < 6.0) {
                now += 1_000
                km += 80.0 / 3600 // the fix() speed, 80 km/h
                location.fix.value = fix(km, now)
                yield()
            }
            assertEquals(listOf(stop), reached) // got there first...
            assertTrue("여주휴게소에 왔어요" in toasts)
            assertEquals(listOf(WaypointEvent(stop, WaypointResult.PASSED)), events) // ...then drove past
            assertNull(nav.route.value.waypoint)
            assertEquals("여주휴게소를 지나쳤어요 · 목적지로 안내해요", nav.route.value.lastAction)
            assertEquals(2, router.requests) // a new route straight to the destination
        } finally { scope.cancel() }
    }
    @Test fun aFailedPrimaryProviderFallsBackAndCancellationDoesNot() = runBlocking {
        val backup = Router(road)
        val failing = object : RouteProvider {
            override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute = error("offline")
        }
        assertSame(road, FallbackRouteProvider(listOf(failing, backup)).route(GeoPoint(37.5, 127.0), null, DemoData.gyeongpo, null))
        assertEquals(1, backup.requests)
        val cancelled = object : RouteProvider {
            override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute = throw CancellationException()
        }
        try {
            FallbackRouteProvider(listOf(cancelled, backup)).route(GeoPoint(37.5, 127.0), null, DemoData.gyeongpo, null)
            fail("cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1, backup.requests) }
    }
}
