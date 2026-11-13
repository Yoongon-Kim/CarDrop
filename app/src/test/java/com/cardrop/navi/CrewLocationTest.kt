package com.cardrop.navi

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CrewLocationTest {
    private val now = 50_000L
    private val gps = GpsFix(37.288748, 127.135952, 80.0, 90.0, now)
    private val sdk = gps.copy(lat = gps.lat + 0.0001, timeMillis = now - 200)

    @Test fun currentSdkMeasurementTakesPriorityOverGps() {
        assertSame(sdk, crewLocationFix(sdk, gps, now))
    }

    @Test fun missingOrStaleSdkMeasurementUsesGps() {
        assertSame(gps, crewLocationFix(null, gps, now))
        assertSame(gps, crewLocationFix(sdk.copy(timeMillis = now - 5_001), gps, now))
    }

    @Test fun emulatorSamplePositionCannotOverrideCurrentGps() {
        val sample = sdk.copy(lat = 37.4127212, lng = 127.094681)
        assertSame(gps, crewLocationFix(sample, gps, now))
    }

    @Test fun samplePositionCannotTakeOverWhenBackgroundGpsUpdatesSlowDown() {
        val sample = sdk.copy(lat = 37.4127212, lng = 127.094681)
        val oldGps = gps.copy(timeMillis = now - 30_000)
        assertSame(oldGps, crewLocationFix(sample, oldGps, now))
    }

    @Test fun invalidSdkCoordinatesUseGps() {
        for (bad in listOf(sdk.copy(lat = Double.NaN), sdk.copy(lng = 181.0), sdk.copy(lat = 0.0, lng = 0.0))) {
            assertSame(gps, crewLocationFix(bad, gps, now))
        }
    }

    @Test fun sdkStillWorksWhenGpsIsUnavailable() {
        assertSame(sdk, crewLocationFix(sdk, null, now))
        assertSame(sdk, crewLocationFix(sdk, gps.copy(timeMillis = now - 10_001), now))
    }

    @Test fun invalidOrFutureReplyCannotInventAPosition() {
        assertNull(crewLocationFix(sdk.copy(timeMillis = now + 1), null, now))
        assertNull(crewLocationFix(null, null, now))
    }
}
