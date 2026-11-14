package com.cardrop.drop

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Example unit test. Run with: ./gradlew testDebugUnitTest */
class VoteRulesTest {

    private val cars = listOf("me", "uncle", "minji", "aunt")
    private val candidates = listOf("a", "b", "c").map { Candidate(it, Place(it, it, 0.0, 0.0), "", 0) }

    private fun drop(votes: Map<String, String>, deadline: Long = 10_000) = Drop(
        id = "d1",
        fromCarId = "me",
        kind = DropKind.DECISION,
        topic = DropTopic.REST,
        title = "t",
        candidates = candidates,
        votes = votes,
        createdAtMillis = 0,
        deadlineMillis = deadline,
    )

    @Test
    fun `cars that did not answer count as candidate 1`() {
        // b has 2 votes, but the 2 silent cars push a to 2 as well; the tie goes to rank 1.
        val d = drop(mapOf("me" to "b", "uncle" to "b"))
        assertEquals("a", VoteRules.winner(d, cars))
    }

    @Test
    fun `majority wins`() {
        val d = drop(mapOf("me" to "b", "uncle" to "b", "minji" to "b"))
        assertEquals("b", VoteRules.winner(d, cars))
        assertEquals(3, VoteRules.agreeing(d, cars))
    }

    @Test
    fun `vote ends when everyone answered or the deadline passed`() {
        assertFalse(VoteRules.isOver(drop(mapOf("me" to "a")), cars, nowMillis = 5_000))
        assertTrue(VoteRules.isOver(drop(mapOf("me" to "a")), cars, nowMillis = 10_000))
        assertTrue(VoteRules.isOver(drop(cars.associateWith { "a" }), cars, nowMillis = 0))
    }

    @Test
    fun `the vote doesn't wait for a car that passed`() {
        val d = drop(mapOf("me" to "b", "uncle" to "b", "minji" to "a", "aunt" to VoteRules.PASS))
        assertTrue(VoteRules.isOver(d, cars, nowMillis = 0))
        assertTrue(VoteRules.passed(d, "aunt"))
        assertFalse(VoteRules.passed(d, "minji"))
    }

    @Test
    fun `a pass counts for no place, not for candidate 1`() {
        // If the three cars that passed counted as silent, a would win 3 to 1.
        val d = drop(mapOf("me" to "b", "uncle" to VoteRules.PASS, "minji" to VoteRules.PASS, "aunt" to VoteRules.PASS))
        assertEquals("b", VoteRules.winner(d, cars))
        assertEquals(1, VoteRules.agreeing(d, cars))
    }

    @Test
    fun `silent cars still count for candidate 1 next to a pass`() {
        // b: me. a: uncle and minji, who didn't answer. aunt passed.
        val d = drop(mapOf("me" to "b", "aunt" to VoteRules.PASS))
        assertEquals("a", VoteRules.winner(d, cars))
        assertEquals(2, VoteRules.agreeing(d, cars))
    }

    @Test
    fun `when every car passes candidate 1 closes the vote`() {
        val d = drop(cars.associateWith { VoteRules.PASS })
        assertTrue(VoteRules.isOver(d, cars, nowMillis = 0))
        assertEquals("a", VoteRules.winner(d, cars))
        assertEquals(0, VoteRules.agreeing(d, cars))
    }

    // ---- quick drops (board 04's pop-up): the sender doesn't vote ----

    @Test
    fun `a quick drop goes to its sender too, who only doesn't vote on it`() {
        val quick = drop(emptyMap()).copy(senderVotes = false)
        assertTrue(quick.isFor("me")) // the decided stop still goes into the sender's route
        assertFalse(quick.isVoter("me"))
        assertTrue(quick.isVoter("uncle"))
        assertEquals(listOf("uncle", "minji", "aunt"), quick.voterIds(cars))
    }

    @Test
    fun `the other cars alone end a quick drop and decide it`() {
        // uncle and aunt b, minji a.
        val votes = mapOf("uncle" to "b", "minji" to "a", "aunt" to "b")
        val quick = drop(votes).copy(senderVotes = false)
        val voters = quick.voterIds(cars)
        assertTrue(VoteRules.isOver(quick, voters, nowMillis = 5_000)) // long before the deadline
        assertEquals("b", VoteRules.winner(quick, voters))
        assertEquals(2, VoteRules.agreeing(quick, voters))
        // The same answers when the sender votes: the vote waits for "me", whose silence counts for a and ties it.
        val usual = drop(votes)
        assertFalse(VoteRules.isOver(usual, usual.voterIds(cars), nowMillis = 5_000))
        assertEquals("a", VoteRules.winner(usual, usual.voterIds(cars)))
    }

    // ---- destination proposals: one place, 찬성 or 반대 ----

    private fun proposal(votes: Map<String, String>, deadline: Long = 10_000) = drop(votes, deadline).copy(
        topic = DropTopic.DESTINATION,
        candidates = candidates.take(1),
    )

    @Test
    fun `only a destination drop with one place is a proposal`() {
        assertTrue(VoteRules.isProposal(proposal(emptyMap())))
        // Three destinations to choose from (the AI's picks) is an ordinary vote.
        assertFalse(VoteRules.isProposal(drop(emptyMap()).copy(topic = DropTopic.DESTINATION)))
        // One rest stop is a vote too: only a new destination is asked as 찬성 / 반대.
        assertFalse(VoteRules.isProposal(drop(emptyMap()).copy(candidates = candidates.take(1))))
    }

    @Test
    fun `a proposal passes when nobody says no, silent cars included`() {
        val d = proposal(mapOf("me" to "a", "uncle" to "a"))
        assertFalse(VoteRules.isOver(d, cars, nowMillis = 5_000)) // minji and aunt can still answer
        assertTrue(VoteRules.isOver(d, cars, nowMillis = 10_000))
        assertEquals("a", VoteRules.winner(d, cars))
        assertEquals(4, VoteRules.agreeing(d, cars))
    }

    @Test
    fun `one no turns a proposal down at once`() {
        val d = proposal(mapOf("me" to "a", "uncle" to "a", "minji" to VoteRules.NO))
        assertTrue(VoteRules.isOver(d, cars, nowMillis = 0)) // doesn't wait for aunt or the deadline
        assertEquals(VoteRules.REJECTED, VoteRules.winner(d, cars))
        assertTrue(VoteRules.saidNo(d, "minji"))
        assertFalse(VoteRules.saidNo(d, "uncle"))
    }

    @Test
    fun `a turned-down proposal has no decided place`() {
        val d = proposal(mapOf("minji" to VoteRules.NO)).copy(decidedCandidateId = VoteRules.REJECTED)
        assertTrue(d.isDecided)
        assertTrue(VoteRules.rejected(d))
        assertEquals(null, d.decidedCandidate) // so the navigator and board 14 leave it alone
        assertEquals(0, VoteRules.agreeing(d, cars))
    }

    @Test
    fun `the sender of a proposal has answered even without a vote`() {
        // A bot's proposal, or board 10 with one destination ticked: the sender ("me") wrote no vote.
        val d = proposal(mapOf("uncle" to "a", "minji" to "a", "aunt" to "a"))
        assertTrue(VoteRules.isOver(d, cars, nowMillis = 0)) // everyone else said 찬성: no need to wait for the timer
        assertEquals("a", VoteRules.winner(d, cars))
        // In an ordinary vote the sender still has to answer.
        assertFalse(VoteRules.isOver(drop(mapOf("uncle" to "a", "minji" to "a", "aunt" to "a")), cars, nowMillis = 0))
    }

    @Test
    fun `a no from a car the proposal was not sent to does not count`() {
        // Sent to uncle only: the voters are me and uncle.
        val d = proposal(mapOf("aunt" to VoteRules.NO)).copy(toCarIds = listOf("uncle"))
        val voters = d.voterIds(cars)
        assertEquals(listOf("me", "uncle"), voters)
        assertFalse(VoteRules.isOver(d, voters, nowMillis = 0))
        assertEquals("a", VoteRules.winner(d, voters))
    }
}
