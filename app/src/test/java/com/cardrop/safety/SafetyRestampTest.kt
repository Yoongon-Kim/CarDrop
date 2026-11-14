package com.cardrop.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host's 출발 comes twice (local guess, then the server's time): that is one trip, not two. */
class SafetyRestampTest {
    @Test
    fun theServerCorrectionOfTheHostsStartIsTheSameTrip() {
        assertTrue(sameTripRestamped("482917-1000000", "482917", recordedStart = 1_000_000, newStart = 1_000_042))
        assertTrue(sameTripRestamped("482917-1000000", "482917", recordedStart = 1_000_000, newStart = 999_000))
    }

    @Test
    fun anotherCrewOrAnotherStartIsANewTrip() {
        assertFalse(sameTripRestamped(null, "482917", 1_000_000, 1_000_042)) // nothing recorded yet
        assertFalse(sameTripRestamped("111111-1000000", "482917", 1_000_000, 1_000_042)) // another crew
        assertFalse(sameTripRestamped("482917-1000000", "482917", 1_000_000, 1_000_000 + 600_000)) // much later
    }

    @Test
    fun retagKeepsTheRecordingUnderTheNewId() {
        val detector = SafetyDetector(SafetyRecord("482917-1000000", "dad", "아빠 차", "경포", 1_000_000, 1_000_000, false), 0)
        detector.retag("482917-1000042", 1_000_042)
        assertEquals("482917-1000042", detector.record.tripId)
        assertEquals(1_000_042L, detector.record.startedAtMillis)
        assertEquals(SafetyTripStatus.RECORDING, detector.record.status)
    }
}
