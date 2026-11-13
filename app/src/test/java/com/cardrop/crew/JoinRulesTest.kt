package com.cardrop.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 03's plain rules. */
class JoinRulesTest {

    @Test
    fun theCodeCanBeHeardAsNumbers() {
        assertEquals("482917", JoinRules.codeFromSpeech("코드 482917"))
        assertEquals("482917", JoinRules.codeFromSpeech("482 917"))
        assertEquals("482917", JoinRules.codeFromSpeech("4-8-2-9-1-7"))
        assertEquals("482917", JoinRules.codeFromSpeech("482917이요"))
    }

    @Test
    fun theCodeCanBeHeardAsKoreanDigitWords() {
        assertEquals("482917", JoinRules.codeFromSpeech("사팔이 구일칠"))
        assertEquals("482917", JoinRules.codeFromSpeech("사 팔 이, 구 일 칠"))
        assertEquals("482917", JoinRules.codeFromSpeech("코드는 사팔이구일칠이에요"))
        assertEquals("100605", JoinRules.codeFromSpeech("일공공육영오"))
    }

    @Test
    fun aCodeEndingIn2BeforeYoKeepsItsLastDigit() {
        // "…일이요": 이 is the digit 2, then 요.
        assertEquals("482912", JoinRules.codeFromSpeech("사팔이구일이요"))
    }

    @Test
    fun anythingElseIsNotACode() {
        assertNull(JoinRules.codeFromSpeech("배고파")) // what the fake voice always hears
        assertNull(JoinRules.codeFromSpeech("삼촌 차"))
        assertNull(JoinRules.codeFromSpeech("48291")) // five digits
        assertNull(JoinRules.codeFromSpeech("4829170")) // seven digits
        assertNull(JoinRules.codeFromSpeech(""))
    }
}
