package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 04's crew strip: where each car stands on the road, its rank, and the gap to the leader. */
class CrewStripTest {
    private val cars = listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt)
    private fun at(km: Float, eta: Int, id: String) = id to CarPosition(id, 0.0, 0.0, km, eta)

    @Test fun theLeaderIsOnTheRightAndTheLastCarOnTheLeft() {
        val strip = crewStrip(cars, mapOf(
            at(100f, 60, DemoData.me.id), at(100.8f, 61, DemoData.uncle.id),
            at(101.8f, 62, DemoData.minji.id), at(103f, 63, DemoData.aunt.id),
        ))
        assertEquals(listOf(DemoData.me.id, DemoData.uncle.id, DemoData.minji.id, DemoData.aunt.id), strip.map { it.car.id })
        assertEquals(listOf(1, 2, 3, 4), strip.map { it.rank })
        assertEquals(0.86f, strip.first().along, 0.001f)
        assertEquals(0.13f, strip.last().along, 0.001f)
        assertTrue(strip.zipWithNext().all { (a, b) -> a.along > b.along })
        assertEquals(61, strip[1].minutes)
    }
    @Test fun carsAtTheSameSpotAreSpreadEvenlyAndCarsWithoutAPositionAreLeftOut() {
        val strip = crewStrip(cars, mapOf(at(50f, 30, DemoData.me.id), at(50f, 30, DemoData.uncle.id)))
        assertEquals(2, strip.size)
        assertEquals(0.86f, strip[0].along, 0.001f)
        assertEquals(0.13f, strip[1].along, 0.001f)
        assertTrue(crewStrip(cars, emptyMap()).isEmpty())
    }
    @Test fun theCarInFrontIsDrawnLastSoItIsOnTopWhenMarkersOverlap() {
        // 아빠 차 leads with 삼촌 차 50 m behind (their markers overlap); 이모 차 is 3 km back.
        val strip = crewStrip(cars, mapOf(
            at(100f, 60, DemoData.me.id), at(100.05f, 60, DemoData.uncle.id), at(103f, 63, DemoData.aunt.id),
        ))
        assertEquals(listOf(DemoData.aunt.id, DemoData.uncle.id, DemoData.me.id), drawingOrder(strip).map { it.car.id })
    }
    @Test fun theGapSaysWhoLeadsAndHowFarBehindTheOthersAre() {
        assertEquals("선두", gapText(100f, 100f))
        assertEquals("2.1km 뒤", gapText(100f, 102.1f))
        assertEquals("12km 뒤", gapText(100f, 112.2f))
    }
}
