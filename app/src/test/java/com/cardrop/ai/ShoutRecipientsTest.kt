package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 09's "받는 차" line (target line 402). */
class ShoutRecipientsTest {
    private val me = DemoData.me.id
    private val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, me, listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt))

    @Test
    fun theWholeCrewWithItsNumberOfCars() {
        assertEquals("크루 전체 (3대)", shoutRecipients(crew, emptyList(), me))
        // Every other car chosen one by one is the whole crew too.
        assertEquals("크루 전체 (3대)", shoutRecipients(crew, listOf(DemoData.uncle.id, DemoData.minji.id, DemoData.aunt.id), me))
    }

    @Test
    fun chosenCarsByName() {
        assertEquals("삼촌 차 · 민지 차", shoutRecipients(crew, listOf(DemoData.minji.id, DemoData.uncle.id), me))
    }

    @Test
    fun noCrewYet() {
        assertEquals("크루 전체", shoutRecipients(null, emptyList(), me))
    }
}
