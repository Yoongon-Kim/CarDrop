package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 01's member rows. */
class MemberRulesTest {
    private val me = DemoData.me
    private val uncle = DemoData.uncle
    private val aunt = DemoData.aunt.copy(connected = false)

    @Test
    fun theHostRowShowsThisCarsFuel() {
        assertEquals("방장 · 연료 64%", MemberRules.meta(me, hostCarId = me.id, myCarId = me.id, myFuelPercent = 64, joinedAgo = null))
        // Another car is the host: its fuel isn't shared.
        assertEquals("방장", MemberRules.meta(uncle, hostCarId = uncle.id, myCarId = me.id, myFuelPercent = 64, joinedAgo = null))
        assertEquals("방장", MemberRules.meta(me, hostCarId = me.id, myCarId = me.id, myFuelPercent = null, joinedAgo = null))
    }

    @Test
    fun theOtherRowsSayHowTheCarJoinedOrThatItIsAwaited() {
        assertEquals("코드로 참여 · 방금 전", MemberRules.meta(uncle, me.id, me.id, 64, joinedAgo = "방금 전"))
        assertEquals("코드로 참여", MemberRules.meta(uncle, me.id, me.id, 64, joinedAgo = null))
        assertEquals("초대 보냄 · 응답 기다리는 중", MemberRules.meta(aunt, me.id, me.id, 64, joinedAgo = null))
    }

    @Test
    fun agoTextCountsMinutesThenHours() {
        assertEquals("방금 전", MemberRules.agoText(59_000))
        assertEquals("1분 전", MemberRules.agoText(60_000))
        assertEquals("59분 전", MemberRules.agoText(59 * 60_000L + 59_000))
        assertEquals("2시간 전", MemberRules.agoText(2 * 3_600_000L))
    }

    @Test
    fun onlyCarsSeenConnectingGetATime() {
        val waiting = Crew("482917", "강릉 여행", DemoData.gyeongpo, me.id, listOf(me, uncle.copy(connected = false), aunt))
        // First look: 아빠 차 is already in, so it gets no time.
        var times = JoinTimes().next(waiting, nowMillis = 1_000)
        assertNull(times.agoText(me.id, nowMillis = 1_000))

        // 삼촌 차 connects while the screen is open.
        val joined = waiting.copy(cars = listOf(me, uncle, aunt))
        times = times.next(joined, nowMillis = 5_000)
        assertEquals("방금 전", times.agoText(uncle.id, nowMillis = 20_000))
        assertEquals("2분 전", times.agoText(uncle.id, nowMillis = 5_000 + 2 * 60_000))

        // It leaves again: no time any more.
        times = times.next(waiting, nowMillis = 9_000)
        assertNull(times.agoText(uncle.id, nowMillis = 9_000))
    }

    @Test
    fun anotherCrewStartsOver() {
        val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, me.id, listOf(me, uncle))
        val times = JoinTimes().next(crew.copy(cars = listOf(me)), 0).next(crew, 1_000)
        val other = times.next(crew.copy(code = "123456"), 2_000)
        assertNull(other.agoText(uncle.id, 2_000))
    }
}
