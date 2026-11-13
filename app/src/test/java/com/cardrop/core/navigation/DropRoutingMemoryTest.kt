package com.cardrop.core.navigation

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.drop.ProposalRules
import com.cardrop.drop.VoteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DropRoutingEffect starts again in every new activity (day/night theme, language, a second MainActivity).
 * Its memory lives in AppContainer, so a drop it already opened must not open (or be said aloud) again.
 */
class DropRoutingMemoryTest {
    private val me = DemoData.me.id
    private val stop = DemoData.restStops[0]

    private fun drop(
        id: String,
        from: String = DemoData.uncle.id,
        kind: DropKind = DropKind.DECISION,
        toCarIds: List<String> = emptyList(),
        decided: Boolean = false,
    ): Drop {
        val candidate = Candidate(stop.id, stop, "test", 2)
        return Drop(
            id = id, fromCarId = from, kind = kind, topic = DropTopic.REST, title = "test",
            candidates = listOf(candidate), votes = emptyMap(), createdAtMillis = 1, deadlineMillis = 0,
            decidedCandidateId = if (decided) candidate.id else null, toCarIds = toCarIds,
        )
    }

    @Test
    fun `a drop from another car opens once, also after a restart`() {
        val memory = DropRoutingMemory({ me })
        val vote = drop("vote")

        assertEquals(listOf(vote), memory.newFromOthers(listOf(vote)))
        // After a restart the effect sees the same list again, with the same memory.
        assertEquals(emptyList<Drop>(), memory.newFromOthers(listOf(vote)))
        // A fresh memory, which every restart used to make, would open the old vote again.
        assertEquals(listOf(vote), DropRoutingMemory({ me }).newFromOthers(listOf(vote)))
    }

    @Test
    fun `only drops that arrived since last time are new`() {
        val memory = DropRoutingMemory({ me })
        val first = drop("first")
        val social = drop("social", from = DemoData.aunt.id, kind = DropKind.SOCIAL)

        memory.newFromOthers(listOf(first))

        assertEquals(listOf(social), memory.newFromOthers(listOf(first, social)))
    }

    @Test
    fun `this car's own drops and drops sent only to other cars never open`() {
        val memory = DropRoutingMemory({ me })
        val mine = drop("mine", from = me)
        val notForMe = drop("not-for-me", toCarIds = listOf(DemoData.minji.id))
        val forMe = drop("for-me", toCarIds = listOf(me))

        assertEquals(listOf(forMe), memory.newFromOthers(listOf(mine, notForMe, forMe)))
    }

    @Test
    fun `a finished vote opens board 14 once, also after a restart`() {
        val memory = DropRoutingMemory({ me })
        val decided = drop("decided", decided = true)

        assertEquals(decided, memory.decidedToOpen(crew = null, drops = listOf(decided)))
        assertNull(memory.decidedToOpen(crew = null, drops = listOf(decided)))
    }

    @Test
    fun `the car picked when joining counts from then on`() {
        // The memory lives as long as the app, but with the real crew this device picks its car on board 03.
        var carId = me
        val memory = DropRoutingMemory({ carId })
        val toAunt = drop("to-aunt", toCarIds = listOf(DemoData.aunt.id))
        val byAunt = drop("by-aunt", from = DemoData.aunt.id)

        assertEquals(listOf(byAunt), memory.newFromOthers(listOf(toAunt, byAunt)))

        carId = DemoData.aunt.id // joined as 이모 차
        val laterByAunt = drop("later-by-aunt", from = DemoData.aunt.id)
        assertEquals(listOf(toAunt), memory.newFromOthers(listOf(toAunt, byAunt, laterByAunt)))
    }

    /** 삼촌 차's proposal to go to 속초 instead, with [votes] so far; [rejected] = one car said 반대 and it ended. */
    private fun proposal(id: String, votes: Map<String, String> = emptyMap(), rejected: Boolean = false): Drop {
        val draft = ProposalRules.draft(DemoData.destinations[2], toCarIds = emptyList())
        return Drop(
            id = id, fromCarId = DemoData.uncle.id, kind = draft.kind, topic = draft.topic, title = draft.title,
            candidates = draft.candidates, votes = votes, createdAtMillis = 1, deadlineMillis = 0,
            decidedCandidateId = if (rejected) VoteRules.REJECTED else null,
        )
    }

    @Test
    fun `a proposal turned down is said once, to the cars that did not say no`() {
        val memory = DropRoutingMemory({ me })
        val open = proposal("p1")
        assertEquals(emptyList<Drop>(), memory.newlyRejected(listOf(open)))

        // 민지 차 said 반대: this car hears it once.
        val down = proposal("p1", votes = mapOf(DemoData.minji.id to VoteRules.NO), rejected = true)
        assertEquals(listOf(down), memory.newlyRejected(listOf(down)))
        assertEquals(emptyList<Drop>(), memory.newlyRejected(listOf(down)))

        // The car that said 반대 itself knows already.
        val mine = DropRoutingMemory({ me })
        mine.newlyRejected(listOf(proposal("p2")))
        val saidNo = proposal("p2", votes = mapOf(me to VoteRules.NO), rejected = true)
        assertEquals(emptyList<Drop>(), mine.newlyRejected(listOf(saidNo)))
    }

    @Test
    fun `a proposal that was over before this car saw it is not said`() {
        // A reconnect delivers the history: a proposal turned down long ago stays quiet.
        val old = proposal("old", votes = mapOf(DemoData.minji.id to VoteRules.NO), rejected = true)
        assertEquals(emptyList<Drop>(), DropRoutingMemory({ me }).newlyRejected(listOf(old)))
    }
}
