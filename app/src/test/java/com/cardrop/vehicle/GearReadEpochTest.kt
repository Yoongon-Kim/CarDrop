package com.cardrop.vehicle

import org.junit.Assert.*
import org.junit.Test

class GearReadEpochTest {
    @Test fun driveEventInvalidatesAnEarlierParkQuery() {
        val reads = GearReadEpoch()
        val oldParkQuery = reads.request()
        reads.event()
        assertFalse(reads.complete(oldParkQuery))
    }
    @Test fun outOfOrderAndDuplicateRepliesCannotRefreshAGearLease() {
        val reads = GearReadEpoch()
        val old = reads.request()
        val latest = reads.request()
        assertFalse(reads.complete(old))
        assertTrue(reads.complete(latest))
        assertFalse(reads.complete(latest))
    }
    @Test fun aFreshQueryAfterTheEventCanConfirmTheCurrentGear() {
        val reads = GearReadEpoch()
        reads.event()
        assertTrue(reads.complete(reads.request()))
    }
}
