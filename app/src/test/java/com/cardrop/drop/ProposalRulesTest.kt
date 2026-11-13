package com.cardrop.drop

import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The destination proposal: what is sent, who a counter-proposal goes to, and what board 11 shows. */
class ProposalRulesTest {

    private val cars = listOf("dad", "uncle", "minji", "aunt")
    private val sokcho = Place("sokcho", "속초 해수욕장", 38.19, 128.60, "강원 속초시")
    private val gyeongpo = Place("gyeongpo", "강릉 경포해변", 37.80, 128.90)

    /** A proposal from 삼촌 차, as the repository stores it. */
    private fun proposal(
        votes: Map<String, String> = emptyMap(),
        toCarIds: List<String> = emptyList(),
        decided: String? = null,
    ): Drop {
        val draft = ProposalRules.draft(sokcho, toCarIds)
        return Drop(
            id = "p1",
            fromCarId = "uncle",
            kind = draft.kind,
            topic = draft.topic,
            title = draft.title,
            message = draft.message,
            candidates = draft.candidates,
            votes = votes,
            createdAtMillis = 0,
            deadlineMillis = 120_000,
            decidedCandidateId = decided,
            toCarIds = draft.toCarIds,
        )
    }

    @Test
    fun `the draft is a destination decision with the one place`() {
        val draft = ProposalRules.draft(sokcho, listOf("minji"))
        assertEquals(DropKind.DECISION, draft.kind)
        assertEquals(DropTopic.DESTINATION, draft.topic)
        assertEquals("속초 해수욕장으로 목적지를 바꿀까요?", draft.title) // 으로 after a consonant
        assertEquals("강원 속초시", draft.message)
        assertEquals(listOf(sokcho), draft.candidates.map { it.place })
        assertEquals(listOf("minji"), draft.toCarIds)
        assertTrue(VoteRules.isProposal(proposal()))
    }

    @Test
    fun `the title takes 로 after a vowel`() {
        val beach = Place("surfy", "양양 서피비치", 38.03, 128.71)
        assertEquals("양양 서피비치로 목적지를 바꿀까요?", ProposalRules.draft(beach, emptyList()).title)
    }

    @Test
    fun `a counter-proposal goes to the same cars`() {
        // To the whole crew: the answer goes to the whole crew too.
        assertEquals(emptyList<String>(), ProposalRules.counterAudience(proposal(), "minji"))
        // 삼촌 sent it to 민지 and 이모: 민지's answer goes to 삼촌 and 이모.
        val some = proposal(toCarIds = listOf("minji", "aunt"))
        assertEquals(listOf("aunt", "uncle"), ProposalRules.counterAudience(some, "minji"))
    }

    @Test
    fun `the place the crew is heading to is the current one`() {
        assertTrue(ProposalRules.isCurrent(gyeongpo, gyeongpo))
        // Kakao's search gives the same beach another id: the name still matches.
        assertTrue(ProposalRules.isCurrent(gyeongpo.copy(id = "kakao-123"), gyeongpo))
        assertFalse(ProposalRules.isCurrent(sokcho, gyeongpo))
        assertFalse(ProposalRules.isCurrent(sokcho, null))
    }

    @Test
    fun `a counter-proposal can't go to the current destination or the place it answers`() {
        assertEquals("지금 가고 있는 목적지예요. 다른 곳을 골라 주세요.", ProposalRules.whyNot(gyeongpo, gyeongpo, answering = null))
        // 다른 목적지 제안하기 on 삼촌's 속초 proposal: 속초 again would be no other destination.
        assertEquals("방금 제안된 곳이에요. 다른 곳을 골라 주세요.", ProposalRules.whyNot(sokcho.copy(id = "kakao-9"), gyeongpo, sokcho))
        assertEquals(null, ProposalRules.whyNot(Place("surfy", "양양 서피비치", 38.03, 128.71), gyeongpo, sokcho))
    }

    @Test
    fun `a turned-down proposal says who said no, this car as 우리 차`() {
        val dad = Car("dad", "아빠 차 (나)", CarColor.BLUE, isMe = true)
        val minji = Car("minji", "민지 차", CarColor.YELLOW)
        val aunt = Car("aunt", "이모 차", CarColor.TEAL)
        val voters = listOf(dad, minji, aunt)
        val byMe = proposal(votes = mapOf("dad" to VoteRules.NO), decided = VoteRules.REJECTED)
        assertEquals("우리 차가 반대해서 목적지는 그대로예요", rejectedText(byMe, voters, myCarId = "dad"))
        val byTwo = proposal(votes = mapOf("minji" to VoteRules.NO, "aunt" to VoteRules.NO), decided = VoteRules.REJECTED)
        assertEquals("민지 차 · 이모 차가 반대해서 목적지는 그대로예요", rejectedText(byTwo, voters, myCarId = "dad"))
    }

    @Test
    fun `a receiver is asked until it answers, the sender only waits`() {
        val open = proposal()
        assertEquals(ProposalView.ASK, proposalView(open, "minji", cars))
        assertEquals(ProposalView.WAITING, proposalView(open, "uncle", cars)) // the proposer
        assertEquals(ProposalView.WAITING, proposalView(proposal(votes = mapOf("minji" to "sokcho")), "minji", cars))
    }

    @Test
    fun `the end shows as accepted or rejected`() {
        assertEquals(ProposalView.ACCEPTED, proposalView(proposal(decided = "sokcho"), "minji", cars))
        val no = proposal(votes = mapOf("aunt" to VoteRules.NO), decided = VoteRules.REJECTED)
        assertEquals(ProposalView.REJECTED, proposalView(no, "minji", cars))
    }

    @Test
    fun `each car's answer on screen`() {
        val open = proposal(votes = mapOf("dad" to "sokcho", "aunt" to VoteRules.NO))
        assertEquals(ProposalAnswer.YES, answerOf(open, "dad"))
        assertEquals(ProposalAnswer.NO, answerOf(open, "aunt"))
        assertEquals(ProposalAnswer.YES, answerOf(open, "uncle")) // the proposer, without a written vote
        assertEquals(ProposalAnswer.WAITING, answerOf(open, "minji"))
        // Passed with 민지 silent: the rule counted her as 찬성, so the screen says so.
        assertEquals(ProposalAnswer.YES, answerOf(proposal(decided = "sokcho"), "minji"))
        // Turned down: a car that said nothing stays "대기 중".
        assertEquals(ProposalAnswer.WAITING, answerOf(proposal(votes = mapOf("aunt" to VoteRules.NO), decided = VoteRules.REJECTED), "minji"))
    }
}
