package com.cardrop.drop

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an inbox row says (board 07, target lines 667-673). */
class InboxRowTest {
    private val stops = DemoData.restStops.take(3) // 덕평자연휴게소, 여주휴게소, 문막휴게소

    private fun drop(
        topic: DropTopic = DropTopic.REST,
        kind: DropKind = DropKind.DECISION,
        title: String = "어디서 쉬어갈까요?",
        message: String = "",
        candidates: Int = 3,
        decided: Boolean = false,
    ): Drop {
        val list = stops.take(candidates).map { Candidate(it.id, it, "", 0) }
        return Drop(
            id = "d", fromCarId = DemoData.me.id, kind = kind, topic = topic, title = title, message = message,
            candidates = list, createdAtMillis = 0, deadlineMillis = 120_000,
            decidedCandidateId = if (decided) list.first().id else null,
        )
    }

    @Test
    fun anOpenVoteNamesTheSenderThePlacesAndTheTimeLeft() {
        val row = inboxRowText(drop(), "아빠 차", DropStatus.NEEDS_ANSWER, nowMillis = 12_000)
        assertEquals(InboxRowText("아빠 차: 어디서 쉬어갈까요?", "덕평 · 여주 · 문막 · 1:48 남음"), row)
        // Already answered: still open, same text. Time is up: no countdown.
        assertEquals(row, inboxRowText(drop(), "아빠 차", DropStatus.VOTING, nowMillis = 12_000))
        assertEquals("덕평 · 여주 · 문막", inboxRowText(drop(), "아빠 차", DropStatus.VOTING, nowMillis = 130_000).sub)
    }

    @Test
    fun aShoutIsQuotedUnderTheSendersName() {
        val shout = drop(DropTopic.MESSAGE, DropKind.SOCIAL, title = "천천히 와요~", candidates = 0)
        assertEquals(InboxRowText("삼촌 차: “천천히 와요~”", "일행에게 한마디"), inboxRowText(shout, "삼촌 차", DropStatus.SOCIAL, 0))
    }

    @Test
    fun aFuelInviteKeepsItsTitleAndSaysItIsNotAnsweredYet() {
        val demo = drop(DropTopic.FUEL, title = "민지 차 연료가 부족해요 · 같이 갈래요?", message = "민지 차 · 주행 가능 85km", candidates = 1)
        assertEquals(
            InboxRowText("민지 차 연료가 부족해요 · 같이 갈래요?", "같이 갈지 아직 안 골랐어요"),
            inboxRowText(demo, "민지 차", DropStatus.NEEDS_ANSWER, 0),
        )
        // A real invite's title doesn't name the car, so the name goes in front. Answered: its message.
        val real = drop(DropTopic.FUEL, title = "연료가 부족해요 · 같이 주유할까요?", message = "주행 가능 85km · 덕평자연휴게소 9km", candidates = 1)
        assertEquals(
            InboxRowText("민지 차: 연료가 부족해요 · 같이 주유할까요?", "주행 가능 85km · 덕평자연휴게소 9km"),
            inboxRowText(real, "민지 차", DropStatus.VOTING, 0),
        )
    }

    @Test
    fun aFinishedVoteSaysWhatWasDecided() {
        assertEquals("결정: 덕평자연휴게소", inboxRowText(drop(decided = true), "아빠 차", DropStatus.DECIDED, 0).sub)
    }

    @Test
    fun restStopsGetTheDesignsShortNames() {
        assertEquals("덕평", shortPlaceName("덕평자연휴게소"))
        assertEquals("여주", shortPlaceName("여주휴게소"))
        assertEquals("강문 스타벅스", shortPlaceName("강문 스타벅스"))
        assertEquals("휴게소", shortPlaceName("휴게소"))
    }
}
