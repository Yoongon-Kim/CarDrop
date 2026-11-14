package com.cardrop.safety

import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SafetyRecordTest {
    private val r = SafetyRecord("trip", "dad", "내 차", "도착지", 100, 100_000, true,
        events = listOf(SafetyEvent("e", SafetyEventType.ACCELERATION, 5000, 2, 3.4)),
        eligibleSeconds = 100.0, observedSeconds = 99.0, movingSeconds = 90.0,
        distanceKm = 2.5, accelerationLocked = true, revision = 3)

    @Test fun journalRoundTripKeepsEveryEventAndRecoveryField() {
        assertEquals(r, safetyRecordFromJson(JSONObject(r.toJson().toString())))
        val done = r.copy(status = SafetyTripStatus.COMPLETED, endedAtMillis = 110_000)
        assertEquals(done, safetyRecordFromJson(JSONObject(done.toJson().toString())))
        assertEquals(98, done.score)
    }

    @Test fun firebaseRoundTripAndInvalidQualityRejection() {
        assertEquals(r.peer(), safetyPeerFromFirebase("dad", r.peer().toFirebase()))
        assertNull(safetyPeerFromFirebase("dad", mapOf("score" to 100)))
        assertNull(safetyPeerFromFirebase("dad", r.peer().toFirebase() + ("speedCoverage" to 2.0)))
    }

    @Test fun rankingsKeepTiesAndExcludePartialStaleMixedModeAndIncompleteRecords() {
        val cars = (1..7).map { Car("$it", "차 $it", CarColor.BLUE) }
        val good = r.peer()
        val peers = mapOf("1" to good.copy(carId = "1", score = 97), "2" to good.copy(carId = "2", score = 97),
            "3" to good.copy(carId = "3", score = 95), "4" to good.copy(carId = "4", speedCoverage = .1),
            "5" to good.copy(carId = "5", updatedAtMillis = 0), "6" to good.copy(carId = "6", demo = false),
            "7" to good.copy(carId = "7", status = SafetyTripStatus.STOPPED))
        val ranked = rankSafetyCars(cars, peers, 100_000, true).associateBy { it.car.id }
        assertEquals(1, ranked["1"]?.rank); assertEquals(1, ranked["2"]?.rank); assertEquals(3, ranked["3"]?.rank)
        (4..7).forEach { assertNull(ranked["$it"]?.rank) }
    }

    @Test fun completedRecordsRemainComparableAfterTheyAreOld() {
        assertTrue(r.copy(status = SafetyTripStatus.COMPLETED).peer().comparable(999_999, true))
        assertFalse(r.copy(status = SafetyTripStatus.STOPPED).rankEligible)
        assertFalse(r.copy(movingSeconds = 59.9).rankEligible)
    }

    @Test fun liveRanksAppearBeforeFinalQualityThresholdsAndChangeWithPenalties() {
        val cars = listOf(Car("dad", "내 차", CarColor.BLUE), Car("uncle", "다른 차", CarColor.PURPLE))
        val early = r.peer().copy(movingSeconds = 5.0, speedCoverage = .92, score = 100, totalPenalty = 0)
        val peers = mapOf("dad" to early, "uncle" to early.copy(carId = "uncle", score = 98, totalPenalty = 2))
        assertNull(rankSafetyCars(cars, peers, 100_000, true).first { it.car.id == "dad" }.rank)
        val live = rankSafetyCars(cars, peers, 100_000, true, live = true)
        assertEquals("dad", live.first().car.id); assertEquals(1, live.first().rank); assertTrue(live.first().provisional)
        val after = rankSafetyCars(cars, peers + ("dad" to early.copy(score = 97, totalPenalty = 3)), 100_000, true, live = true)
        assertEquals("uncle", after.first().car.id); assertEquals(2, after.first { it.car.id == "dad" }.rank)
    }

    @Test fun provisionalRanksNeverMixMeasuredCategoriesOrUseStaleOrUnmeasuredCars() {
        val cars = (1..6).map { Car("$it", "차 $it", CarColor.BLUE) }
        val p = r.peer()
        val peers = mapOf("1" to p.copy(carId = "1", score = 90), "2" to p.copy(carId = "2", rulesVersion = 1, score = 100),
            "3" to p.copy(carId = "3", speedCoverage = 0.0), "4" to p.copy(carId = "4", updatedAtMillis = 0),
            "5" to p.copy(carId = "5", demo = false), "6" to p.copy(carId = "6", status = SafetyTripStatus.STOPPED))
        val ranks = rankSafetyCars(cars, peers, 100_000, true, live = true).associateBy { it.car.id }
        assertEquals(1, ranks["1"]?.rank); assertEquals("급가감속", ranks["1"]?.basis)
        assertNull(ranks["2"]?.rank)
        (3..6).forEach { assertNull(ranks["$it"]?.rank) }
    }
    @Test fun legacyJournalRetainsMotionPenaltiesAndRemovesRetiredEvents() {
        val old = r.toJson().put("rulesVersion", 1)
        old.getJSONArray("events").put(JSONObject().apply {
            put("id", "old-speeding"); put("type", "SPEEDING"); put("at", 6000)
            put("penalty", 7); put("duration", 125.0); put("peak", 120.0); put("limit", 80)
        })
        val migrated = safetyRecordFromJson(old)
        assertEquals(SafetyRules.VERSION, migrated.rulesVersion)
        assertEquals(r.events, migrated.events)
        assertEquals(98, migrated.score)
        assertEquals(r.startedAtMillis, migrated.startedAtMillis)
        assertEquals(r.accelerationLocked, migrated.accelerationLocked)
        assertTrue(migrated.rankEligible)
        assertEquals(migrated, safetyRecordFromJson(migrated.toJson()))
    }
}
