package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.navi.WaypointResult
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading the crew's "도착했어요" / "지나쳤어요" messages back for board 14. */
class StopNewsTest {
    private val yeoju = DemoData.restStops[1]
    private val munmak = DemoData.restStops[2]

    private fun message(from: String, title: String, at: Long, kind: DropKind = DropKind.SOCIAL) =
        Drop("m$at", from, kind, DropTopic.MESSAGE, title, createdAtMillis = at, deadlineMillis = 0)

    @Test fun eachCarsLatestMessageAboutThePlaceSinceTheVoteCounts() {
        val drops = listOf(
            message(DemoData.me.id, StopNews.arrived(yeoju), at = 50), // an earlier visit, before this vote
            message(DemoData.uncle.id, StopNews.passed(yeoju), at = 120),
            message(DemoData.minji.id, StopNews.arrived(munmak), at = 130), // another place
            message(DemoData.aunt.id, "뒤에 사고 났어요, 조심하세요", at = 140), // a plain 한마디
            message(DemoData.me.id, StopNews.passed(yeoju), at = 150),
            message(DemoData.me.id, StopNews.arrived(yeoju), at = 160), // came back and stopped
        )
        assertEquals(
            mapOf(DemoData.uncle.id to WaypointResult.PASSED, DemoData.me.id to WaypointResult.ARRIVED),
            StopNews.results(drops, yeoju, sinceMillis = 100),
        )
    }

    @Test fun theTextIsWhatTheDriverAndCrewRead() {
        assertEquals("여주휴게소에 도착했어요", StopNews.arrived(yeoju))
        assertEquals("여주휴게소를 지나쳤어요", StopNews.passed(yeoju))
    }

    @Test fun onlyCrewMessagesCount() {
        val vote = message(DemoData.uncle.id, StopNews.arrived(yeoju), at = 120, kind = DropKind.DECISION)
        assertEquals(emptyMap<String, WaypointResult>(), StopNews.results(listOf(vote), yeoju, sinceMillis = 100))
    }
}
