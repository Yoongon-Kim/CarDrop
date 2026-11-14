package com.cardrop.safety

import org.junit.Assert.*
import org.junit.Test

class SafetyDetectorTest {
    private val initial = SafetyRecord("trip", "dad", "내 차", "목적지", 0, 0, true)
    private fun sample(engine: SafetyDetector, tenths: Int, speed: Double, paused: Boolean = false) {
        val t = tenths * 100_000_000L
        engine.ingest(SpeedReading(speed, t, t), t / 1_000_000, paused)
    }

    @Test fun steadyAndGentleDrivingNeverEarnsOrLosesPoints() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..200) sample(d, i, 8 + i * .05)
        assertTrue(d.record.events.isEmpty()); assertEquals(100, d.record.score)
        assertEquals(20.0, d.record.observedSeconds, .00001)
    }

    @Test fun sustainedAccelerationIsOneEventUntilAFullNormalSecondRearmsIt() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..40) sample(d, i, 10 + 3.4 * i / 10.0)
        assertEquals(1, d.record.count(SafetyEventType.ACCELERATION)); assertEquals(98, d.record.score)
        for (i in 41..65) sample(d, i, 23.6)
        for (i in 66..85) sample(d, i, 23.6 + 3.4 * (i - 65) / 10.0)
        assertEquals(2, d.record.count(SafetyEventType.ACCELERATION))
    }

    @Test fun sustainedBrakingAndLowSpeedBrakingHaveDifferentOutcomes() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..20) sample(d, i, 20 - 4.0 * i / 10.0)
        assertEquals(1, d.record.count(SafetyEventType.BRAKING)); assertEquals(97, d.record.score)
        val slow = SafetyDetector(initial, 0)
        for (i in 0..10) sample(slow, i, (4 - 4.0 * i / 10.0).coerceAtLeast(0.0))
        assertEquals(0, slow.record.count(SafetyEventType.BRAKING))
    }

    @Test fun shortSpikeAndMeasurementGapsDoNotBecomeConfirmedEvents() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..7) sample(d, i, 10 + 3.4 * i / 10.0)
        for (i in 30..37) sample(d, i, 12.38 + 3.4 * (i - 30) / 10.0)
        assertEquals(0, d.record.events.size)
        assertTrue(d.record.speedCoverage < .5)
    }

    @Test fun duplicateOutOfOrderStaleInvalidAndReverseSamplesCannotCreateEvents() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..30) {
            sample(d, i, 20.0)
            sample(d, i, 90.0)
            val t = i * 100_000_000L
            d.ingest(SpeedReading(90.0, t - 2_000_000_000, t), i * 100L)
        }
        sample(d, 31, Double.NaN); sample(d, 32, Double.POSITIVE_INFINITY); sample(d, 33, 500.0)
        for (i in 34..64) sample(d, i, -10.0)
        assertTrue(d.record.events.isEmpty())
    }

    @Test fun parkAndReversePauseMeasurementAndDoNotResetTheScore() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..20) sample(d, i, 20 - 4.0 * i / 10.0)
        val score = d.record.score
        val moving = d.record.movingSeconds
        for (i in 21..100) sample(d, i, 10 + i.toDouble(), paused = true)
        assertEquals(score, d.record.score); assertEquals(moving, d.record.movingSeconds, .000001)
    }

    @Test fun constantHighSpeedNeverDeductsPoints() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..650) sample(d, i, 50.0)
        assertTrue(d.record.events.isEmpty())
        assertEquals(100, d.record.score)
        assertTrue(d.record.rankEligible)
    }

    @Test fun restoringConfirmedLocksDoesNotDuplicateMotionEvents() {
        val d = SafetyDetector(initial, 0)
        for (i in 0..20) sample(d, i, 10 + 3.4 * i / 10.0)
        val restored = SafetyDetector(d.record, 10_000_000_000)
        for (i in 100..120) sample(restored, i, 25 + 3.4 * (i - 100) / 10.0)
        assertEquals(1, restored.record.count(SafetyEventType.ACCELERATION))
        assertEquals(98, restored.record.score)
    }

    @Test fun completedRecordsFreezeAndLargePenaltiesStopAtZero() {
        val events = List(60) { SafetyEvent("e$it", SafetyEventType.ACCELERATION, 0, 2) }
        val d = SafetyDetector(initial.copy(events = events), 0)
        assertEquals(0, d.record.score)
        d.finish(1000, true)
        val finished = d.record
        for (i in 10..40) sample(d, i, 40 - 4.0 * (i - 10) / 10.0)
        assertEquals(finished, d.record)
    }

    @Test fun bufferedMeasurementsStillUseTheirMeasurementTimeAfterUiDelay() {
        val d = SafetyDetector(initial, 0)
        d.tick(10_000_000_000L, 10_000, false)
        for (i in 0..20) {
            val t = i * 100_000_000L
            d.ingest(SpeedReading(10 + 3.4 * i / 10.0, t, t), 10_000)
        }
        assertEquals(1, d.record.count(SafetyEventType.ACCELERATION))
        assertEquals(98, d.record.score)
        assertEquals(10.0, d.record.eligibleSeconds, .00001)
        assertEquals(2.0, d.record.observedSeconds, .00001)
    }
}
