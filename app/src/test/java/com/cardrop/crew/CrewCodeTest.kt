package com.cardrop.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CrewCodeTest {
    @Test fun aCodeIsSixDigitsWithoutALeadingZero() {
        repeat(1_000) { seed ->
            val code = newCrewCode(Random(seed))
            assertEquals(code, 6, code.length)
            assertTrue(code, code.all(Char::isDigit) && code.first() != '0')
        }
    }

    @Test fun newCrewsGetDifferentCodes() {
        val random = Random(7) // fixed seed: the same codes on every run
        val codes = List(100) { newCrewCode(random) }.toSet()
        // 100 codes out of 900,000: a repeat is possible but rare, so allow a couple.
        assertTrue("only ${codes.size} different codes", codes.size >= 98)
    }
}
