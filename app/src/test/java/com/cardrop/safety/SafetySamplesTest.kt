package com.cardrop.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Board 16's sample trips for the 시연: this week's, finished, and never doubled. */
class SafetySamplesTest {
    /** Sunday 2026-11-15 02:00 on this machine's clock. */
    private val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 2, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun threeFinishedTripsOfThisWeek() {
        val trips = SafetySamples.week("dad", "아빠 차", now)
        assertEquals(listOf("속초 해수욕장", "양양 서피비치", "춘천 남이섬"), trips.map { it.destination })
        assertEquals(listOf(96, 91, 100), trips.map { it.score })
        val weekStart = startOfWeek(now)
        trips.forEach {
            assertTrue(it.startedAtMillis in weekStart until now) // "이번 주"
            assertEquals(SafetyTripStatus.COMPLETED, it.status)
            assertTrue(it.rankEligible) // counted in "n회 완주 · 평균 n점"
            assertTrue(it.demo)
            assertEquals("dad", it.carId)
        }
        assertEquals(96, trips.map { it.score }.average().let { Math.round(it).toInt() })
    }

    @Test
    fun theWeekStartsOnMonday() {
        val monday = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 9, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals(monday, startOfWeek(now))
    }

    @Test
    fun samplesAreAddedOnceNewestFirst() {
        val stored = SafetySamples.week("dad", "아빠 차", now).take(1)
        val all = SafetySamples.withSamples(stored, "dad", "아빠 차", now)
        assertEquals(3, all.size)
        assertEquals(all.sortedByDescending { it.updatedAtMillis }, all)
    }
}
