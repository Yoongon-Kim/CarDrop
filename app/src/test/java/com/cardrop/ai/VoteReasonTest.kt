package com.cardrop.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 11's line under the title: why these places are in the vote (target line 712). */
class VoteReasonTest {

    @Test
    fun onlyTheAisPicksSayHowManyCarsTheyFit() {
        assertEquals("AI가 4대 모두 가기 좋은 곳으로 골랐어요", voteReason(cars = 4, aiPicks = 3, added = 0))
        assertEquals("AI가 2대 모두 가기 좋은 곳으로 골랐어요", voteReason(cars = 2, aiPicks = 2, added = 0))
    }

    @Test
    fun aCarAloneGetsNoCount() {
        assertEquals("AI가 가기 좋은 곳으로 골랐어요", voteReason(cars = 1, aiPicks = 3, added = 0))
    }

    @Test
    fun placesTheDriverAddedAreNamedAsSuch() {
        assertEquals("AI가 고른 2곳에 직접 고른 1곳을 더했어요", voteReason(cars = 4, aiPicks = 2, added = 1))
        assertEquals("직접 고른 2곳 중에서 골라요", voteReason(cars = 4, aiPicks = 0, added = 2))
    }
}
