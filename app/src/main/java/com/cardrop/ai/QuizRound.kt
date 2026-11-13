package com.cardrop.ai

import com.cardrop.core.model.QuizQuestion

/** Questions in one round of board 15's quiz (the design's "3 / 10 문제" counter, shortened for the demo). */
const val QUIZ_ROUND = 5

/** Board 15's games. The design also lists 오목 and 체스; those are cut for week 1. */
enum class QuizKind(val title: String, val meta: String, val icon: String) {
    INITIALS("초성 퀴즈", "4대 다 같이", "ㅊ"),
    LOCAL("지역 퀴즈", "AI가 지금 위치로 출제", "?"),
}

/**
 * Where a quiz round stands: which game, which question ([number], 1-based), how many were right, the answer
 * picked for the current question, and who is challenged ([opponentId] null = 크루 전체).
 */
data class QuizRound(
    val kind: QuizKind = QuizKind.LOCAL,
    val number: Int = 1,
    val correct: Int = 0,
    val picked: String? = null,
    val opponentId: String? = null,
    val challengeSent: Boolean = false,
) {
    val finished: Boolean get() = number > QUIZ_ROUND

    /** The driver picks [choice]; only the first pick of a question counts. */
    fun answered(choice: String, question: QuizQuestion): QuizRound =
        if (picked != null || finished) this
        else copy(picked = choice, correct = correct + if (choice == question.correctAnswer) 1 else 0)

    fun next(): QuizRound = copy(number = number + 1, picked = null)

    /** A fresh round of [kind] with the same opponent. */
    fun restarted(kind: QuizKind = this.kind): QuizRound = QuizRound(kind = kind, opponentId = opponentId)
}

/** Board 15 "초성 퀴즈": snacks sold at 휴게소, by their first consonants. Built in, so it needs no AI call. */
object InitialsQuiz {
    private const val HINT = "휴게소에서 파는 간식 · 먼저 맞히는 차가 점수!"

    val questions = listOf(
        QuizQuestion(HINT, listOf("ㅎ", "ㄷ", "ㄱ", "ㅈ"), listOf("호두과자", "핫도그", "회오리감자", "핫바"), "호두과자"),
        QuizQuestion(HINT, listOf("ㅅ", "ㄸ", "ㅅ", "ㄸ"), listOf("순대볶음", "소떡소떡", "식혜", "쌀국수"), "소떡소떡"),
        QuizQuestion(HINT, listOf("ㅇ", "ㄱ", "ㅈ"), listOf("오징어", "어묵", "알감자", "우동"), "알감자"),
        QuizQuestion(HINT, listOf("ㄸ", "ㅂ", "ㅇ"), listOf("떡갈비", "붕어빵", "땅콩과자", "떡볶이"), "떡볶이"),
        QuizQuestion(HINT, listOf("ㅎ", "ㄷ", "ㄱ"), listOf("호두과자", "햄버거", "핫도그", "핫바"), "핫도그"),
    )

    /** The question for round question [number] (1-based). */
    fun question(number: Int): QuizQuestion = questions[(number - 1).mod(questions.size)]
}

private val CHOSEONG = listOf("ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

/** The first consonant (초성) of each Hangul syllable in [word], e.g. "호두과자" -> ㅎ ㄷ ㄱ ㅈ. */
fun initials(word: String): List<String> = word.filter { it in '가'..'힣' }.map { CHOSEONG[(it - '가') / 588] }
