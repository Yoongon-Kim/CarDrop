package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.drop.VoteRules
import com.cardrop.navi.WaypointResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Board 14's "도착 예정": who stops, how far, and when. */
class ArrivalsTest {
    private val now = 1_700_000_000_000L
    private val zone = ZoneId.of("Asia/Seoul")
    private val me = DemoData.me.id
    private val uncle = DemoData.uncle.id
    private val minji = DemoData.minji.id
    private val aunt = DemoData.aunt.id
    private val cars = listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt)
    private val stop = DemoData.restStops[0]

    // This car leads; the others are 0.8, 1.8 and 3 km behind (1, 2 and 3 minutes later).
    private val positions = mapOf(
        me to CarPosition(me, 0.0, 0.0, 100f, 60),
        uncle to CarPosition(uncle, 0.0, 0.0, 100.8f, 61),
        minji to CarPosition(minji, 0.0, 0.0, 101.8f, 62),
        aunt to CarPosition(aunt, 0.0, 0.0, 103f, 63),
    )

    private fun decided(topic: DropTopic = DropTopic.REST, from: String = uncle, votes: Map<String, String> = emptyMap(),
        toCarIds: List<String> = emptyList()): Drop {
        val winner = Candidate(stop.id, stop, "test", 2)
        return Drop("d", from, DropKind.DECISION, topic, "test", candidates = listOf(winner), votes = votes,
            createdAtMillis = 1, deadlineMillis = 0, decidedCandidateId = winner.id, toCarIds = toCarIds)
    }

    private fun clockIn(minutes: Int) =
        Instant.ofEpochMilli(now + minutes * 60_000L).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun rows(drop: Drop, skipped: Boolean = false, myKm: Double? = 9.0, myMinutes: Double? = 6.0,
        at: Map<String, CarPosition> = positions, said: Map<String, WaypointResult> = emptyMap()) =
        crewArrivals(drop, cars, at, me, skipped, myKm, myMinutes, "강릉 경포해변", now, zone, said)

    @Test fun everyCarIsTimedFromThisCarsWayToTheStop() {
        val rows = rows(decided())
        assertEquals(listOf(me, uncle, minji, aunt), rows.map { it.car.id })
        assertEquals("9.0km", rows[0].status)
        assertEquals(clockIn(6), rows[0].time)
        assertEquals("9.8km", rows[1].status)
        assertEquals(clockIn(7), rows[1].time)
        assertEquals("12km", rows[3].status)
    }
    @Test fun aFuelStopIsForTheSenderAndTheCarsThatJoinedOnly() {
        val rows = rows(decided(DropTopic.FUEL, from = minji, votes = mapOf(me to stop.id)))
        assertEquals(listOf(me, minji, uncle, aunt), rows.map { it.car.id })
        assertEquals("패스 · 강릉 경포해변에서 합류", rows[2].status)
        assertEquals("계속", rows[2].time)
    }
    @Test fun turningTheStopDownOrNotBeingInTheVoteMeansDrivingOn() {
        val rows = rows(decided(from = me, toCarIds = listOf(uncle)), skipped = true)
        assertEquals(listOf(uncle), rows.filter { it.stops }.map { it.car.id })
        assertEquals("패스 · 강릉 경포해변에서 합류", rows.first { it.car.id == me }.status)
    }
    @Test fun aCarThatPassedOnTheVoteDrivesOn() {
        // 이모 said "난 패스, 먼저 갈게" on board 11 (target line 753: "패스 · 경포해변에서 합류").
        val rows = rows(decided(votes = mapOf(aunt to VoteRules.PASS)))
        val row = rows.first { it.car.id == aunt }
        assertEquals("패스 · 강릉 경포해변에서 합류", row.status)
        assertEquals("계속", row.time)
        assertEquals(listOf(me, uncle, minji), rows.filter { it.stops }.map { it.car.id })
        // The sender of a fuel stop who passed on it doesn't stop either.
        val fuel = rows(decided(DropTopic.FUEL, from = minji, votes = mapOf(minji to VoteRules.PASS)))
        assertFalse(fuel.first { it.car.id == minji }.stops)
    }
    @Test fun nearTheStopWithoutAMessageIsAlmostThereAndUnknownPositionsAreSaidPlainly() {
        val rows = rows(decided(), myKm = 0.1, myMinutes = 0.0, at = mapOf(me to positions.getValue(me)))
        assertEquals("곧 도착해요", rows.first { it.car.id == me }.status) // beside the stop, not stopped yet
        assertEquals("위치 기다리는 중", rows.first { it.car.id == uncle }.status)
    }
    @Test fun aCarThatSaidItStoppedHasArrivedEvenAfterDrivingOn() {
        val rows = rows(decided(), myKm = -5.0, said = mapOf(me to WaypointResult.ARRIVED))
        assertEquals("도착했어요", rows.first { it.car.id == me }.status)
        assertEquals(me, rows.first().car.id)
    }
    @Test fun aCarThatSaidItDrovePastMeetsTheCrewAtTheDestination() {
        val rows = rows(decided(), said = mapOf(uncle to WaypointResult.PASSED))
        val row = rows.first { it.car.id == uncle }
        assertEquals("지나쳤어요 · 강릉 경포해변에서 합류", row.status)
        assertEquals("계속", row.time)
        assertFalse(row.stops)
        assertEquals(uncle, rows.last().car.id) // after the cars that stop
    }
    @Test fun withoutAMessageOnlyNearIsAlmostThereAndWellPastIsDrivenPast() {
        // This car is 2 km past the stop; the others are 0.8, 1.8 and 3 km behind it.
        val rows = rows(decided(), myKm = -2.0).associate { it.car.id to it.status }
        assertEquals("지나쳤어요 · 강릉 경포해변에서 합류", rows[me])
        assertEquals("지나쳤어요 · 강릉 경포해변에서 합류", rows[uncle]) // 1.2 km past
        assertEquals("곧 도착해요", rows[minji]) // 0.2 km past
        assertEquals("1.0km", rows[aunt])
    }
}
