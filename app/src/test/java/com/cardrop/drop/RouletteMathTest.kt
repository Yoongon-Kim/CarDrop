package com.cardrop.drop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the wheel stops on the slice it was told to. Run with: ./gradlew testDebugUnitTest */
class RouletteMathTest {

    @Test
    fun beforeSpinning_pointerIsOverSliceOne() {
        assertEquals(0, RouletteMath.landedIndex(0f, 3))
    }

    @Test
    fun spinStopsOnTheWinner_forEveryWinnerAndStartAngle() {
        for (count in 2..5) {
            for (winner in 0 until count) {
                for (start in listOf(0f, 37f, 400f, 1234.5f)) {
                    val end = RouletteMath.targetRotation(start, winner, count)
                    assertEquals("count=$count winner=$winner start=$start", winner, RouletteMath.landedIndex(end, count))
                }
            }
        }
    }

    @Test
    fun spinAlwaysTurnsForwardAtLeastFourTimes() {
        val start = 100f
        val end = RouletteMath.targetRotation(start, winner = 1, count = 3)
        assertTrue(end - start >= 4 * 360f)
        assertTrue(end - start < 5 * 360f)
    }
}
