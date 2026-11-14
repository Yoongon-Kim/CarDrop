package com.cardrop.navi

import ai.pleos.playground.location.fused.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FusedLocationMeasurementTest {
    private val now = 12_000_000L
    private val gps = GpsFix(37.288747, 127.135952, 0.0, null, now)

    @Test fun newlyReceivedThreeHourOldCacheDoesNotBecomeCurrent() {
        val cached = LocationData(gps.lat, gps.lng, 50.0, 0f, 5f, now - 3 * 60 * 60 * 1000)
        val measured = cached.toCrewGpsFix()
        assertEquals(cached.timestampMillis, measured.timeMillis)
        assertSame(gps, crewLocationFix(measured, gps, now))
        assertNull(crewLocationFix(measured, null, now))
    }

    @Test fun freshMeasurementIsUsedAndExpiresEvenWithoutAnotherSdkReply() {
        val sdk = LocationData(gps.lat + 0.0001, gps.lng, 50.0, 90f, 5f, now - 100).toCrewGpsFix()
        assertSame(sdk, crewLocationFix(sdk, gps, now))
        assertSame(gps, crewLocationFix(sdk, gps, now + 5_000))
        assertNull(currentCrewFix(sdk, now + 5_000))
    }

    @Test fun invalidHeadingDoesNotDiscardUsableCoordinates() {
        val sdk = LocationData(gps.lat, gps.lng, 50.0, Float.NaN, 5f, now).toCrewGpsFix()
        assertNull(sdk.bearing)
        assertSame(sdk, crewLocationFix(sdk, gps, now))
    }
}
