package com.cardrop.core

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.recipientLabel
import com.cardrop.drop.VoteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 08 "받는 차": who a drop is for, who votes, and how the screens name the receivers. */
class RecipientsTest {

    private val me = DemoData.me
    private val uncle = DemoData.uncle
    private val minji = DemoData.minji
    private val aunt = DemoData.aunt
    private val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, me.id, listOf(me, uncle, minji, aunt))
    private val crewIds = crew.cars.map { it.id }

    private fun drop(toCarIds: List<String>, votes: Map<String, String> = emptyMap()) = Drop(
        id = "d1",
        fromCarId = me.id,
        kind = DropKind.DECISION,
        topic = DropTopic.REST,
        title = "t",
        candidates = DemoData.restStops.take(2).map { Candidate(it.id, it, "", 2) },
        votes = votes,
        createdAtMillis = 0,
        deadlineMillis = 10_000,
        toCarIds = toCarIds,
    )

    @Test
    fun aDropForTheWholeCrewIsForEveryone() {
        val drop = drop(toCarIds = emptyList())
        assertTrue(crewIds.all { drop.isFor(it) })
        assertEquals(crewIds, drop.voterIds(crewIds))
    }

    @Test
    fun aDropForChosenCarsIsForThemAndTheSender() {
        val drop = drop(toCarIds = listOf(minji.id))
        assertTrue(drop.isFor(me.id))
        assertTrue(drop.isFor(minji.id))
        assertFalse(drop.isFor(uncle.id))
        assertEquals(listOf(me.id, minji.id), drop.voterIds(crewIds))
    }

    @Test
    fun onlyTheChosenCarsCountInTheVote() {
        // 민지 picks the 2nd place; the uncle wasn't asked, so his "vote" for #1 doesn't count, and the
        // vote is over once both voters answered.
        val drop = drop(toCarIds = listOf(minji.id), votes = mapOf(me.id to "yeoju", minji.id to "yeoju", uncle.id to "deokpyeong"))
        val voters = drop.voterIds(crewIds)
        assertTrue(VoteRules.isOver(drop, voters, nowMillis = 0))
        assertEquals("yeoju", VoteRules.winner(drop, voters))
    }

    @Test
    fun namesTheReceivers() {
        assertEquals("크루 전체", crew.recipientLabel(emptyList(), me.id))
        assertEquals("민지 차", crew.recipientLabel(listOf(minji.id), me.id))
        // crew order, not the order they were tapped
        assertEquals("삼촌 차 · 민지 차", crew.recipientLabel(listOf(minji.id, uncle.id), me.id))
        assertEquals("크루 전체", crew.recipientLabel(listOf(uncle.id, minji.id, aunt.id), me.id))
    }
}
