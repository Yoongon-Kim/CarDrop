package com.cardrop.core.text

/**
 * Owner: Lead.
 *
 * Adds the Korean particle that fits the word's last syllable: [afterConsonant] when it ends in a
 * final consonant (받침), else [afterVowel]. "여주휴게소".particle("을", "를") = "여주휴게소를",
 * "경포호수점".particle("을", "를") = "경포호수점을". Words that don't end in Hangul get [afterVowel].
 * For 로/으로 use [withRo], which has its own rule.
 */
fun String.particle(afterConsonant: String, afterVowel: String): String =
    this + if (finalConsonant() != 0) afterConsonant else afterVowel

/**
 * Adds 로 or 으로: 으로 after a final consonant other than ㄹ. "여주휴게소로", "강릉 경포해변으로",
 * "서울로". Words that don't end in Hangul get 로.
 */
fun String.withRo(): String {
    val final = finalConsonant()
    return this + if (final != 0 && final != FINAL_RIEUL) "으로" else "로"
}

/** The last Hangul syllable's final consonant (0 = none), ignoring trailing spaces and ")". */
private fun String.finalConsonant(): Int {
    val last = lastOrNull { !it.isWhitespace() && it != ')' } ?: return 0
    return if (last in '가'..'힣') (last - '가') % 28 else 0
}

/** ㄹ's index among the final consonants. */
private const val FINAL_RIEUL = 8
