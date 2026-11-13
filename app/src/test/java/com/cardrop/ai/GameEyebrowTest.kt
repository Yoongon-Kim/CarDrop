package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.drive.StopNews
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 15's eyebrow (target line 506). */
class GameEyebrowTest {
    private val me = DemoData.me.id
    private val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, me, listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt))
    private val stop = DemoData.restStops.first() // 덕평자연휴게소

    private val vote = Drop(
        id = "vote", fromCarId = me, kind = DropKind.DECISION, topic = DropTopic.REST, title = "어디서 쉬어갈까요?",
        candidates = listOf(Candidate(stop.id, stop, "", 0)), createdAtMillis = 1_000, deadlineMillis = 121_000,
        decidedCandidateId = stop.id,
    )

    private fun said(carId: String, text: String, at: Long) =
        Drop(id = "$carId-$at", fromCarId = carId, kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = text, createdAtMillis = at, deadlineMillis = at)

    @Test
    fun everyCarStoppedAtTheCrewsStop() {
        val drops = listOf(vote) + listOf(DemoData.uncle, DemoData.minji, DemoData.aunt).map { said(it.id, StopNews.arrived(stop), 200_000) }
        assertEquals("덕평자연휴게소 · 4대 모두 정차", gameEyebrow(stop, crew, me, drops))
    }

    @Test
    fun onlyTheCarsThatSaidTheyArrivedSinceTheVoteCount() {
        val drops = listOf(
            said(DemoData.aunt.id, StopNews.arrived(stop), 500), // before the vote: an earlier visit
            vote,
            said(DemoData.uncle.id, StopNews.arrived(stop), 200_000),
            said(DemoData.minji.id, StopNews.passed(stop), 200_000),
        )
        assertEquals("덕평자연휴게소 · 2대 정차", gameEyebrow(stop, crew, me, drops)) // 삼촌 차 and this car
    }

    @Test
    fun parkedSomewhereElse() {
        assertEquals("정차 중 · 크루 전체", gameEyebrow(null, crew, me, emptyList()))
    }
}
