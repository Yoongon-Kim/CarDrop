package com.cardrop.drop

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 11: what this car's pick sends, and which place it counts for. */
class VotePickTest {
    private val candidates = listOf("a", "b", "c").map { Candidate(it, Place(it, it, 0.0, 0.0), "", 0) }
    private val stops = listOf("덕평자연휴게소", "여주휴게소", "문막휴게소").map { Candidate(it, Place(it, it, 0.0, 0.0), "", 0) }

    @Test
    fun theAiCardSaysWhatEachPickMeans() {
        assertEquals("덕평자연휴게소에 1표" to "“1번”, “첫 번째”도 같은 표예요", VotePick.Place("덕평자연휴게소").explain(stops))
        assertEquals("여주휴게소에 1표" to "“2번”, “두 번째”도 같은 표예요", VotePick.Place("여주휴게소").explain(stops))
        assertEquals(
            "상관없음 → 1순위 덕평자연휴게소에 1표" to "“아무 데나”, “다 좋아”, “알아서 해” 모두 같은 뜻이에요",
            VotePick.Anywhere.explain(stops),
        )
        assertEquals("이번엔 안 들러요" to "우리 차는 경로를 유지하고 결과만 알려드려요", VotePick.Pass.explain(stops))
    }

    @Test
    fun theVoiceSaysWhichAnswerWentOut() {
        assertEquals("여주휴게소에 투표했어요", VotePick.Place("여주휴게소").answeredLine(stops))
        assertEquals("아무 데나 좋다고 답했어요", VotePick.Anywhere.answeredLine(stops))
        assertEquals("이번엔 패스했어요. 우리 차는 가던 길로 가요", VotePick.Pass.answeredLine(stops))
        // A quick drop's one place: 예 / 아니요.
        val one = stops.take(1)
        assertEquals("덕평자연휴게소에 같이 들른다고 답했어요", VotePick.Place("덕평자연휴게소").answeredLine(one, yesNo = true))
        assertEquals("안 들른다고 답했어요. 우리 차는 가던 길로 가요", VotePick.Pass.answeredLine(one, yesNo = true))
        assertEquals("아니요 · 우리 차는 안 들러요", VotePick.Pass.explain(one, yesNo = true).first)
    }

    @Test
    fun aPlaceIsAVoteForThatPlace() {
        assertEquals("b", VotePick.Place("b").vote(candidates))
        assertEquals("b", VotePick.Place("b").countedId(candidates))
    }

    @Test
    fun anywhereIsAVoteForPlaceOne() {
        assertEquals("a", VotePick.Anywhere.vote(candidates))
        assertEquals("a", VotePick.Anywhere.countedId(candidates))
    }

    @Test
    fun aPassSendsThePassAndCountsForNoPlace() {
        assertEquals(VoteRules.PASS, VotePick.Pass.vote(candidates))
        assertNull(VotePick.Pass.countedId(candidates))
    }

    @Test
    fun theScreenStartsWithThisCarsVoteOrPlaceOne() {
        assertEquals(VotePick.Place("a"), VotePick.from(vote = null, candidates))
        assertEquals(VotePick.Place("c"), VotePick.from(vote = "c", candidates))
        assertEquals(VotePick.Pass, VotePick.from(vote = VoteRules.PASS, candidates))
    }
}
