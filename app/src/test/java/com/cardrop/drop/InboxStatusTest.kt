package com.cardrop.drop

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a drop means for this car in the inbox (board 07): its tab, its button and the 응답 필요 count. */
class InboxStatusTest {
    private val me = DemoData.me.id
    private val station = DemoData.restStops[0]

    private fun drop(
        id: String = "fuel",
        from: String = DemoData.minji.id,
        kind: DropKind = DropKind.DECISION,
        topic: DropTopic = DropTopic.FUEL,
        votes: Map<String, String> = emptyMap(),
        decided: Boolean = false,
    ): Drop {
        val candidate = Candidate(station.id, station, "test", 2)
        return Drop(
            id = id, fromCarId = from, kind = kind, topic = topic, title = "test",
            candidates = if (kind == DropKind.DECISION) listOf(candidate) else emptyList(), votes = votes,
            createdAtMillis = 1, deadlineMillis = 0, decidedCandidateId = if (decided) candidate.id else null,
        )
    }

    @Test
    fun anOpenInviteFromAnotherCarNeedsAnAnswer() {
        assertEquals(DropStatus.NEEDS_ANSWER, drop().status(me) { false })
    }

    @Test
    fun aFuelInviteThisCarDeclinedNoLongerNeedsAnAnswer() {
        // 가던 길 갈래요 (board 06) records no vote, only a skip. 홈's badge already leaves it out.
        assertEquals(DropStatus.VOTING, drop(id = "fuel").status(me) { it == "fuel" })
    }

    @Test
    fun answeredOwnFinishedAndSocialDrops() {
        assertEquals(DropStatus.VOTING, drop(votes = mapOf(me to station.id)).status(me) { false })
        assertEquals(DropStatus.VOTING, drop(from = me).status(me) { false })
        assertEquals(DropStatus.DECIDED, drop(id = "fuel", decided = true).status(me) { it == "fuel" })
        assertEquals(DropStatus.SOCIAL, drop(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE).status(me) { false })
    }
}
