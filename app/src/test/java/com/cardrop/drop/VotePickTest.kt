package com.cardrop.drop

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 11: what this car's pick sends, and which place it counts for. */
class VotePickTest {
    private val candidates = listOf("a", "b", "c").map { Candidate(it, Place(it, it, 0.0, 0.0), "", 0) }

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
