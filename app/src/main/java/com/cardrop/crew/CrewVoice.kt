package com.cardrop.crew

import com.cardrop.core.text.particle

/**
 * Owner: Member A. Plain text rules for board 01's 말로 읽어주기 and 링크 문자로 보내기 (unit tested).
 */
object CrewVoice {

    private val koreanDigits = mapOf(
        '0' to "공", '1' to "일", '2' to "이", '3' to "삼", '4' to "사",
        '5' to "오", '6' to "육", '7' to "칠", '8' to "팔", '9' to "구",
    )

    /**
     * Board 01 "말로 읽어주기": "482917" -> "참여 코드는 사 팔 이, 구 일 칠이에요" (ends in 오 -> "…오예요").
     * Digits are written as Korean words so the speech engine reads them one by one,
     * not as "사십팔만 이천…".
     */
    fun codeSentence(code: String): String {
        val spoken = code.filter(Char::isDigit).chunked(3).joinToString(", ") { part ->
            part.map { koreanDigits.getValue(it) }.joinToString(" ")
        }
        return "참여 코드는 ${spoken.particle("이에요", "예요")}"
    }

    /** Board 01 "링크 문자로 보내기": the text message body. */
    fun inviteMessage(crewName: String, code: String): String =
        "CarDrop 크루 '$crewName'에 같이 가요! 차 화면에서 '받은 코드로 참여'를 눌러 주세요. 참여 코드: ${code.chunked(3).joinToString(" ")}"
}
