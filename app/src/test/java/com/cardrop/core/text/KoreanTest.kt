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
        assertEquals("지훈이", "지훈".particle("이", "가"))
        assertEquals("삼촌 차가", "삼촌 차".particle("이", "가"))
        assertEquals("아빠 차 (나)가", "아빠 차 (나)".particle("이", "가")) // the name inside the brackets counts
    }

    @Test
    fun picksRoOrEuroWithTheRieulRule() {
        assertEquals("여주휴게소로", "여주휴게소".withRo())
        assertEquals("강릉 경포해변으로", "강릉 경포해변".withRo())
        assertEquals("경포호수점으로", "경포호수점".withRo())
        assertEquals("서울로", "서울".withRo()) // ends in ㄹ: 로, not 으로
        assertEquals("GS25로", "GS25".withRo())
    }
}
