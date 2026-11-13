package com.cardrop.crew

/**
 * Owner: Member A (board 03's design work: lead). Plain rules for board 03 코드로 참여, kept out of the
 * screen so they can be unit tested (JoinRulesTest).
 */
object JoinRules {
    /** A crew's join code has this many digits, e.g. "482917". The crew is looked up at the last one. */
    const val CODE_LENGTH = 6

    /**
     * The join code in what the driver said (board 03's 말로 key), or null when it isn't exactly 6 digits.
     * The recognizer may write the digits as numbers ("코드 482917", "482 917") or as the Korean words for
     * them ("사팔이 구일칠"; 공 and 영 are 0). "코드" in front and an ending such as "이에요" or "요" are
     * left out. Anything else ("배고파", "삼촌 차") means it wasn't a code.
     */
    fun codeFromSpeech(heard: String): String? {
        // Only letters and digits count: spaces, "-", "," and quotes go.
        val text = heard.filter { it.isLetterOrDigit() }
        // "…이요" can be the ending, or the digit 이 (2) and then 요, so every way of cutting is tried.
        val withoutStart = listOf(text) + WORDS_BEFORE.filter { text.startsWith(it) }.map { text.removePrefix(it) }
        val stems = withoutStart.flatMap { start -> listOf(start) + ENDINGS.filter { start.endsWith(it) }.map { start.removeSuffix(it) } }
        return stems.firstNotNullOfOrNull { stem -> digitsOf(stem)?.takeIf { it.length == CODE_LENGTH } }
    }

    /** [text] as digits, or null when one of its letters is not a digit. */
    private fun digitsOf(text: String): String? = buildString {
        for (c in text) append(if (c in '0'..'9') c else SPOKEN_DIGITS[c] ?: return null)
    }

    private val WORDS_BEFORE = listOf("참여코드는", "참여코드", "코드는", "코드", "번호는", "번호")
    private val ENDINGS = listOf("이에요", "예요", "입니다", "이요", "요")
    private val SPOKEN_DIGITS = mapOf(
        '공' to '0', '영' to '0', '일' to '1', '이' to '2', '삼' to '3', '사' to '4',
        '오' to '5', '육' to '6', '륙' to '6', '칠' to '7', '팔' to '8', '구' to '9',
    )
}
