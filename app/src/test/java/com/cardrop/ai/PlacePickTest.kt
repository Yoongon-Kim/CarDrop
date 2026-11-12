package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 10's rules: ticking places, adding places, and finding a place by name. */
class PlacePickTest {

    private val deokpyeong = DemoData.restStops[0]
    private val yeoju = DemoData.restStops[1]
    private val munmak = DemoData.restStops[2]
    private val pyeongchang = DemoData.restStops[4]
    private val known = DemoData.restStops + DemoData.destinations

    private fun aiPick(vararg places: Place) =
        PlacePick.fromAi(places.mapIndexed { i, place -> Candidate(place.id, place, "reason $i", 2 + i) })

    @Test
    fun allAiPlacesStartTicked() {
        val pick = aiPick(deokpyeong, yeoju, munmak)
        assertEquals(listOf("deokpyeong", "yeoju", "munmak"), pick.selected.map { it.id })
        assertEquals(3, pick.aiCount)
    }

    @Test
    fun toggleUnticksAndTicksAgain() {
        val pick = aiPick(deokpyeong, yeoju, munmak).toggle("yeoju")
        assertEquals(listOf("deokpyeong", "munmak"), pick.selected.map { it.id })
        assertTrue(pick.toggle("yeoju").isSelected("yeoju"))
    }

    @Test
    fun addingANewPlacePutsItLastAndTicksIt() {
        val pick = aiPick(deokpyeong, yeoju).add(pyeongchang)
        assertEquals(listOf("deokpyeong", "yeoju", "pyeongchang"), pick.candidates.map { it.id })
        assertTrue(pick.isSelected("pyeongchang"))
        assertTrue(pick.isAdded("pyeongchang"))
        assertEquals(PlacePick.ADDED_REASON, pick.candidates.last().reason)
        assertEquals(2, pick.aiCount)
    }

    @Test
    fun addingAPlaceAlreadyInTheListOnlyTicksIt() {
        val pick = aiPick(deokpyeong, yeoju).toggle("yeoju").add(yeoju)
        assertEquals(2, pick.candidates.size)
        assertTrue(pick.isSelected("yeoju"))
        assertFalse(pick.isAdded("yeoju"))
    }

    @Test
    fun theAiBestPickStaysFirstInTheVote() {
        val pick = aiPick(deokpyeong, yeoju).add(pyeongchang).toggle("yeoju")
        assertEquals(listOf("deokpyeong", "pyeongchang"), pick.selected.map { it.id })
    }

    @Test
    fun findsPlacesByExactOrPartName() {
        assertEquals(deokpyeong, findKnownPlace("덕평자연휴게소", known))
        assertEquals(deokpyeong, findKnownPlace("덕평", known))
        assertEquals(deokpyeong, findKnownPlace("덕평 휴게소", known))
        assertEquals(pyeongchang, findKnownPlace("평창휴게소.", known))
        assertEquals("gyeongpo", findKnownPlace("경포해변", known)?.id)
    }

    @Test
    fun cafesCanBeFoundByName() {
        val withCafes = known + DemoData.cafes
        assertEquals("cafe-anmok-bossanova", findKnownPlace("보사노바", withCafes)?.id)
        assertEquals("cafe-gangmun-starbucks", findKnownPlace("강문 스타벅스", withCafes)?.id)
    }

    @Test
    fun unknownOrTooShortNamesFindNothing() {
        assertNull(findKnownPlace("투썸플레이스", known + DemoData.cafes))
        assertNull(findKnownPlace("휴게소", known))
        assertNull(findKnownPlace("덕", known))
        assertNull(findKnownPlace("  ", known))
    }
}
