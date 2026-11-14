package com.cardrop.drive

import com.cardrop.core.model.Gear
import com.cardrop.core.model.Place
import com.cardrop.navi.GpsFix
import org.junit.Assert.*
import org.junit.Test

class DriveRulesTest {
    @Test fun warnsOncePerLowFuelEpisodeAndRearmsOnlyAfterRefuelling() {
        val tracker = FuelWarningTracker()
        assertFalse(tracker.update(64))
        assertTrue(tracker.update(15))
        listOf(15, 14, 16, null, 15, 19, 12).forEach { assertFalse(tracker.update(it)) }
        assertFalse(tracker.update(20))
        assertTrue(tracker.update(12))
    }
    @Test fun invalidFuelDoesNotConsumeTheWarning() {
        val tracker = FuelWarningTracker()
        listOf(null, -1, 101).forEach { assertFalse(tracker.update(it)) }
        assertTrue(tracker.update(0))
        tracker.reset()
        assertTrue(tracker.update(0))
    }
    @Test fun gearDecidesThePillAndFreshGpsSpeedOnlyWhenGearIsUnknown() {
        val fix = GpsFix(37.5, 127.0, 80.0, 90.0, 100_000)
        assertEquals(DrivingInput.PARKED, drivingInput(Gear.PARK, null, 100_000))
        listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL).forEach {
            assertEquals(DrivingInput.MOVING, drivingInput(it, null, 100_000))
        }
        assertEquals(DrivingInput.MOVING, drivingInput(Gear.UNKNOWN, fix, 100_000))
        assertEquals(DrivingInput.UNKNOWN, drivingInput(Gear.UNKNOWN, fix, 120_000))
        assertEquals(DrivingInput.UNKNOWN, drivingInput(Gear.UNKNOWN, fix.copy(speedKmh = 0.0), 100_000))
    }
    @Test fun fuelCandidatesMustBeAheadOnTheRoadAndWithinRangeWithReserve() {
        val road = RouteGeometry(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.2)))
        fun station(id: String, km: Double, offset: Double = 0.0, detail: String = "주유소") = road.pointAt(km).let {
            Place(id, id, it.lat + offset, it.lng, detail)
        }
        val behind = station("behind", 1.0)
        val near = station("near", 4.0)
        val far = station("far", 10.0)
        val off = station("off", 4.0, .04)
        val food = station("food", 5.0, detail = "식당")
        val pool = listOf(far, off, food, behind, near)
        assertEquals(listOf(near), reachableFuelStops(pool, road, 2.0, 10f))
        assertEquals(listOf(near, far), reachableFuelStops(pool, road, 2.0, 20f))
        assertEquals(listOf(far), reachableFuelStops(pool, road, 2.0, 20f, minimumAheadKm = 5.0))
        assertTrue(reachableFuelStops(pool, road, 2.0, null).isEmpty())
        assertTrue(reachableFuelStops(pool, road, 2.0, Float.NaN).isEmpty())
    }
    @Test fun ambiguousOrNegativeVoiceRepliesDoNotTriggerAnAction() {
        assertEquals(DriveVoiceCommand.SHARE_FUEL, driveVoiceCommand("응, 알려줘"))
        assertEquals(DriveVoiceCommand.JOIN, driveVoiceCommand("같이 갈래요!"))
        assertEquals(DriveVoiceCommand.JOIN, driveVoiceCommand("같이 가자"))
        assertEquals(DriveVoiceCommand.SKIP, driveVoiceCommand("우리 차는 안 들르기"))
        listOf(null, "", "배고파", "알려주지 마", "같이 갈래요 말래요", "안내 시작하지 마").forEach { assertNull(driveVoiceCommand(it)) }
    }
    @Test fun gamesOpenInParkOnceTheCarHasStopped() {
        val now = 100_000L
        fun fix(speed: Double?, at: Long = now) = GpsFix(37.5, 127.0, speed, 90.0, at)
        // Parked: no GPS, a GPS without a speed, standing still, or creeping under 5 km/h.
        assertTrue(gamesOpen(Gear.PARK, null, now))
        assertTrue(gamesOpen(Gear.PARK, fix(null), now))
        assertTrue(gamesOpen(Gear.PARK, fix(0.0), now))
        assertTrue(gamesOpen(Gear.PARK, fix(4.9), now))
        // P pressed, but the last reading still says 100 km/h: wait for the car to stop.
        assertFalse(gamesOpen(Gear.PARK, fix(100.0), now))
        assertFalse(gamesOpen(Gear.PARK, fix(5.1), now))
        // A reading a little ahead of this clock (the GPS clock differs) is still now.
        assertFalse(gamesOpen(Gear.PARK, fix(100.0, at = now + 900), now))
        // An old reading (over 10 s) says nothing about now.
        assertTrue(gamesOpen(Gear.PARK, fix(100.0, at = now - 10_001), now))
        // Any other gear keeps the games closed, standing still too.
        listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN).forEach {
            assertFalse(gamesOpen(it, null, now))
            assertFalse(gamesOpen(it, fix(0.0), now))
        }
    }
}
