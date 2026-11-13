package com.cardrop.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 15: the quiz round and the built-in 초성 questions. */
class QuizRoundTest {
    @Test fun everyInitialsQuestionHasOneAnswerThatMatchesItsTiles() {
        assertEquals(listOf("ㅎ", "ㄷ", "ㄱ", "ㅈ"), initials("호두과자"))
        InitialsQuiz.questions.forEach { q ->
            assertEquals(q.correctAnswer, q.tiles, initials(q.correctAnswer))
            assertTrue(q.correctAnswer in q.answers)
            assertEquals(4, q.answers.distinct().size)
            q.answers.filter { it != q.correctAnswer }.forEach { assertNotEquals(it, q.tiles, initials(it)) }
        }
    }
    @Test fun onlyTheFirstPickCountsAndTheRoundEndsAfterFiveQuestions() {
        val question = InitialsQuiz.question(1)
        var round = QuizRound(kind = QuizKind.INITIALS)
        round = round.answered(question.correctAnswer, question).answered("핫도그", question)
        assertEquals(1, round.correct)
        assertEquals(question.correctAnswer, round.picked)
        repeat(QUIZ_ROUND - 1) { round = round.next() }
        assertFalse(round.finished)
        round = round.next()
        assertTrue(round.finished)
        val restarted = round.copy(opponentId = "car-uncle").restarted(QuizKind.LOCAL)
        assertEquals(QuizRound(kind = QuizKind.LOCAL, opponentId = "car-uncle"), restarted)
    }
    @Test fun theInitialsQuestionsRepeatPastTheEnd() {
        assertEquals(InitialsQuiz.question(1), InitialsQuiz.question(InitialsQuiz.questions.size + 1))
    }
}
