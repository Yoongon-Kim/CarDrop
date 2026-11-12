package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake AI's rules for the new place types (board 08 카페 / 직접 추가). */
class FakeAiServiceTest {

    // As if no rest stop were ahead any more: cafés must not depend on this filter.
    private val ai = FakeAiService(stopsAhead = { emptyList() })
    private val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, DemoData.me.id, listOf(DemoData.me, DemoData.uncle))

    @Test
    fun coffeeMeansCafe() = runBlocking {
        assertEquals(DropTopic.CAFE, ai.readIntent("커피 한잔하고 싶어").topic)
        assertEquals(DropTopic.CAFE, ai.readIntent("카페 가자").topic)
    }

    @Test
    fun cafePicksComeFromTheCafesNearTheDestination() = runBlocking {
        val picks = ai.recommend(DropTopic.CAFE, TripContext(crew, emptyMap(), VehicleSnapshot(), DemoData.cafes))
        assertEquals(DemoData.cafes.map { it.id }, picks.map { it.id })
    }
}
