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
}
