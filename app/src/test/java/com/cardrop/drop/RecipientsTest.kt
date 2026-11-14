package com.cardrop.drop

import com.cardrop.core.demo.DemoData
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 08's recipients (live canvas): 크루 전체, or any cars ticked one by one. Empty = the whole crew. */
class RecipientsTest {
    private val others = listOf(DemoData.uncle, DemoData.minji, DemoData.aunt)
    private val uncle = DemoData.uncle.id
    private val minji = DemoData.minji.id
    private val aunt = DemoData.aunt.id

    @Test
    fun nothingTickedIsTheWholeCrew() {
        assertEquals(emptyList<String>(), Recipients.toCarIds(others, emptySet()))
    }

    @Test
    fun theTickedCarsGoInCrewOrder() {
        assertEquals(listOf(minji), Recipients.toCarIds(others, setOf(minji)))
        assertEquals(listOf(uncle, aunt), Recipients.toCarIds(others, setOf(aunt, uncle)))
    }

    @Test
    fun everyCarTickedIsTheWholeCrew() {
        assertEquals(emptyList<String>(), Recipients.toCarIds(others, setOf(uncle, minji, aunt)))
    }

    @Test
    fun aCarThatLeftTheCrewIsDropped() {
        assertEquals(emptyList<String>(), Recipients.toCarIds(others, setOf("gone")))
        assertEquals(listOf(minji), Recipients.toCarIds(others, setOf("gone", minji)))
    }

    @Test
    fun tappingACarTicksItAndTappingAgainUnticksIt() {
        val one = Recipients.toggle(emptySet(), minji, others)
        assertEquals(setOf(minji), one)
        val two = Recipients.toggle(one, aunt, others)
        assertEquals(setOf(minji, aunt), two)
        assertEquals(setOf(aunt), Recipients.toggle(two, minji, others))
        // Unticking the only ticked car: back to the whole crew.
        assertEquals(emptySet<String>(), Recipients.toggle(one, minji, others))
    }

    @Test
    fun tickingTheLastCarLeftIsTheWholeCrewAgain() {
        assertEquals(emptySet<String>(), Recipients.toggle(setOf(uncle, minji), aunt, others))
        // A crew of two: the only other car is the whole crew.
        assertEquals(emptySet<String>(), Recipients.toggle(emptySet(), uncle, listOf(DemoData.uncle)))
    }

    @Test
    fun theWholeCrewButtonNamesTheOtherCars() {
        assertEquals("삼촌 · 민지 · 이모", crewMeta(others))
        assertEquals("아직 혼자예요", crewMeta(emptyList()))
    }
}
