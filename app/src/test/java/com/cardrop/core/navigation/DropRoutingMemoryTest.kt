package com.cardrop.core.navigation

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
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
}
