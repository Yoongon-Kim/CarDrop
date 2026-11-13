package com.cardrop.core.navigation

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertEquals
import org.junit.Test

/** A drop opened from the inbox (board 07) must open the same screen as when it arrived (DropRoutingEffect). */
class InboxScreenForTest {
    private val place = DemoData.restStops[0]

    private fun drop(id: String, topic: DropTopic, kind: DropKind = DropKind.DECISION, decided: Boolean = false): Drop {
        val candidate = Candidate(place.id, place, "test", 2)
        return Drop(
            id = id, fromCarId = DemoData.minji.id, kind = kind, topic = topic, title = "test",
            candidates = if (kind == DropKind.DECISION) listOf(candidate) else emptyList(),
            createdAtMillis = 1, deadlineMillis = 0, decidedCandidateId = if (decided) candidate.id else null,
        )
    }

    @Test
    fun aFuelInviteOpensBoard06() {
        // Board 06's 같이 갈래요 puts the station into the navigator right away; board 11 would wait for the vote.
        assertEquals(Screen.FuelThem("fuel"), inboxScreenFor(drop("fuel", DropTopic.FUEL)))
    }

    @Test
    fun otherDecisionDropsOpenTheVote() {
        assertEquals(Screen.Vote("rest"), inboxScreenFor(drop("rest", DropTopic.REST)))
        assertEquals(Screen.Vote("cafe"), inboxScreenFor(drop("cafe", DropTopic.CAFE)))
    }

    @Test
    fun aFinishedVoteOpensTheResult() {
        assertEquals(Screen.Decided("rest"), inboxScreenFor(drop("rest", DropTopic.REST, decided = true)))
        assertEquals(Screen.Decided("fuel"), inboxScreenFor(drop("fuel", DropTopic.FUEL, decided = true)))
    }

    @Test
    fun aSocialDropStaysOnTheInbox() {
        assertEquals(Screen.Inbox, inboxScreenFor(drop("msg", DropTopic.MESSAGE, kind = DropKind.SOCIAL)))
    }
}
