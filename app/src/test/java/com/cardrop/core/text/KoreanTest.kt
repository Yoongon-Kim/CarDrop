package com.cardrop.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanTest {
    @Test
    fun picksTheParticleFromTheLastSyllable() {
        assertEquals("여주휴게소를", "여주휴게소".particle("을", "를"))
        assertEquals("경포호수점을", "경포호수점".particle("을", "를"))
        assertEquals("여주휴게소는", "여주휴게소".particle("은", "는"))
        assertEquals("강릉 경포해변은", "강릉 경포해변".particle("은", "는"))
        assertEquals("GS25를", "GS25".particle("을", "를")) // not Hangul: the vowel form
    }
}
