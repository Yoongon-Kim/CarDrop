package com.cardrop.crew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewVoiceTest {

    @Test
    fun `code is read digit by digit in two groups`() {
        assertEquals("참여 코드는 사 팔 이, 구 일 칠이에요", CrewVoice.codeSentence("482917"))
        assertEquals("참여 코드는 공 일 공, 육 오 오예요", CrewVoice.codeSentence("010 655"))
    }

    @Test
    fun `invite text has the crew name and the code in two groups`() {
        val text = CrewVoice.inviteMessage("강릉 여행", "482917")
        assertTrue(text.contains("'강릉 여행'"))
        assertTrue(text.endsWith("482 917"))
    }
}
