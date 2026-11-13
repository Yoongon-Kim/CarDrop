package com.cardrop.drop

import com.cardrop.core.demo.DemoData
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 08's four recipient buttons (D13): who a drop goes to. Empty = the whole crew. */
class SendToTest {
    private val others = listOf(DemoData.uncle, DemoData.minji, DemoData.aunt)
    private val uncle = DemoData.uncle.id
    private val minji = DemoData.minji.id
    private val aunt = DemoData.aunt.id

    @Test
    fun theWholeCrewIsNoCarInParticular() {
        assertEquals(emptyList<String>(), SendTo.Crew.toCarIds(others, mixIds = setOf(uncle)))
    }

    @Test
    fun oneCarAloneGoesToThatCar() {
        assertEquals(listOf(minji), SendTo.OneCar(minji).toCarIds(others, mixIds = emptySet()))
        // The car left the crew since: the whole crew, rather than nobody.
        assertEquals(emptyList<String>(), SendTo.OneCar("gone").toCarIds(others, mixIds = emptySet()))
    }

    @Test
    fun aCombinationIsTheTickedCarsInCrewOrder() {
        assertEquals(listOf(uncle, aunt), SendTo.Combination.toCarIds(others, mixIds = setOf(aunt, uncle)))
    }

    @Test
    fun aCombinationOfNoCarOrEveryCarIsTheWholeCrew() {
        assertEquals(emptyList<String>(), SendTo.Combination.toCarIds(others, mixIds = emptySet()))
        assertEquals(emptyList<String>(), SendTo.Combination.toCarIds(others, mixIds = setOf(uncle, minji, aunt)))
    }

    @Test
    fun theWholeCrewButtonNamesTheOtherCars() {
        assertEquals("삼촌 · 민지 · 이모", crewMeta(others))
        assertEquals("아직 혼자예요", crewMeta(emptyList()))
    }
}
