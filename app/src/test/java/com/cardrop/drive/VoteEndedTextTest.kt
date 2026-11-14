package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 14's first line names whose vote ended, so another car's result doesn't look like this car's own. */
class VoteEndedTextTest {
    private val me = DemoData.me.id
    private val cars = listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt)
    private val places = listOf("a", "b", "c").map { Candidate(it, Place(it, it, 0.0, 0.0), "", 0) }

    /** A finished vote that place 1 won. */
    private fun decided(
        from: String,
        topic: DropTopic = DropTopic.REST,
        candidates: List<Candidate> = places,
        votes: Map<String, String> = emptyMap(),
        toCarIds: List<String> = emptyList(),
    ) = Drop(
        id = "d1",
        fromCarId = from,
        kind = DropKind.DECISION,
        topic = topic,
        title = "t",
        candidates = candidates,
        votes = votes,
        createdAtMillis = 0,
        deadlineMillis = 0,
        decidedCandidateId = candidates.first().id,
        toCarIds = toCarIds,
    )

    @Test fun anotherCarsVoteNamesThatCar() {
        // 민지 picked b; the three cars that said nothing count for a, the winner.
        val drop = decided(from = DemoData.uncle.id, votes = mapOf(DemoData.minji.id to "b"))
        assertEquals("삼촌 차의 휴게소 투표가 끝났어요 · 4대 중 3대 동의", voteEndedText(drop, cars, me))
    }

    @Test fun thisCarsOwnVoteSaysOurCar() {
        val drop = decided(from = me, topic = DropTopic.FOOD)
        assertEquals("우리 차의 식사 투표가 끝났어요 · 4대 중 4대 동의", voteEndedText(drop, cars, me))
    }

    @Test fun aSenderNoLongerInTheCrewIsIlhaeng() {
        val drop = decided(from = DemoData.aunt.id)
        assertEquals("일행의 휴게소 투표가 끝났어요 · 3대 중 3대 동의", voteEndedText(drop, cars - DemoData.aunt, me))
    }

    @Test fun onlyTheCarsTheVoteWasForAreCounted() {
        // 민지 sent it to this car only; this car picked b, 민지 said nothing (a).
        val drop = decided(from = DemoData.minji.id, votes = mapOf(me to "b"), toCarIds = listOf(me))
        assertEquals("민지 차의 휴게소 투표가 끝났어요 · 2대 중 1대 동의", voteEndedText(drop, cars, me))
    }

    @Test fun aDestinationProposalNamesItsTopic() {
        val proposal = decided(from = DemoData.uncle.id, topic = DropTopic.DESTINATION, candidates = places.take(1))
        assertEquals("삼촌 차의 목적지 변경 투표가 끝났어요 · 4대 중 4대 동의", voteEndedText(proposal, cars, me))
    }
}
