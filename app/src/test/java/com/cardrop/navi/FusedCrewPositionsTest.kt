package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.core.model.CarPosition
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class FusedCrewPositionsTest {
    @Test fun sdkPositionDrivesTheDiagramsRemainingDistanceAndStopsWithTheCrew() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var now = 50_000L
        val geometry = RouteGeometry(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.1)))
        fun fixAt(km: Double) = geometry.pointAt(km).let { GpsFix(it.lat, it.lng, null, it.bearing, now) }
        val gps = FixedLocation(fixAt(1.0))
        val sdk = FixedLocation(fixAt(1.2))
        val other = CarPosition("other", 37.5, 127.08, 1f, 1)
        val shared = MutableStateFlow(mapOf("other" to other, "me" to other.copy(carId = "me")))
        val live = LiveRoute(geometry, emptyList(), listOf(geometry.totalKm to geometry.totalKm * 60), null, "test")
        val router = object : RouteProvider {
            override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?) = live
        }
        try {
            val navigation = InAppNavigation(scope, gps, router, now = { now })
            val destination = Place("end", "end", 37.5, 127.1)
            navigation.setRoute(destination)
            val cars = GpsCrewPositions(scope, navigation, { "me" }, shared, preferredLocation = sdk, now = { now })
            cars.start(listOf("me", "other"), destination)

            val mine = cars.positions.value.getValue("me")
            assertEquals(sdk.fix.value!!.lat, mine.lat, 0.000001)
            assertEquals(sdk.fix.value!!.lng, mine.lng, 0.000001)
            assertEquals(geometry.totalKm - 1.2, mine.kmToDestination.toDouble(), 0.01)
            assertTrue(sdk.started)
            assertSame(other, cars.positions.value["other"])

            // New SDK measurements move this car and its route progress; shared peers keep their own coordinates.
            now += 1_000
            sdk.fix.value = fixAt(1.6)
            assertEquals(geometry.totalKm - 1.6, cars.positions.value.getValue("me").kmToDestination.toDouble(), 0.01)
            assertSame(other, cars.positions.value["other"])

            // Expiration/unavailability switches this car back to the navigator GPS without losing peers.
            gps.fix.value = fixAt(1.0) // the navigator advances after its next GPS tick, as on a device
            sdk.fix.value = null
            assertEquals(geometry.totalKm - 1.0, cars.positions.value.getValue("me").kmToDestination.toDouble(), 0.01)
            assertSame(other, cars.positions.value["other"])

            cars.stop()
            assertTrue(sdk.stopped)
            assertTrue(cars.positions.value.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    private class FixedLocation(initial: GpsFix) : LocationSource {
        override val fix = MutableStateFlow<GpsFix?>(initial)
        var started = false
        var stopped = false
        override fun start(): Boolean { started = true; return true }
        override fun stop() { stopped = true }
    }
}
