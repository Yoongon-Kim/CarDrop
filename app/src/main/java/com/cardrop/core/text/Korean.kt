package com.cardrop.core.text

/**
 * Owner: Lead.
 *
 * Adds the Korean particle that fits the word's last syllable: [afterConsonant] when it ends in a
 * final consonant (받침), else [afterVowel]. "여주휴게소".particle("을", "를") = "여주휴게소를",
 * "경포호수점".particle("을", "를") = "경포호수점을". Words that don't end in Hangul get [afterVowel].
 */
fun String.particle(afterConsonant: String, afterVowel: String): String {
    val last = lastOrNull { !it.isWhitespace() && it != ')' }
    val hasFinal = last != null && last in '가'..'힣' && (last - '가') % 28 != 0
    return this + if (hasFinal) afterConsonant else afterVowel
}
