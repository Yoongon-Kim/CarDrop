package com.cardrop.ai

import com.cardrop.core.model.DropTopic
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
    fun afterSendingTheVoiceNamesTheTopicLikeTheTiles() {
        assertEquals("휴게소 투표를 보냈어요", voteSentLine(DropTopic.REST))
        assertEquals("식사 투표를 보냈어요", voteSentLine(DropTopic.FOOD))
        assertEquals("주유 투표를 보냈어요", voteSentLine(DropTopic.FUEL))
    }

    @Test
    fun placesTheDriverAddedAreNamedAsSuch() {
        assertEquals("AI가 고른 2곳에 직접 고른 1곳을 더했어요", voteReason(cars = 4, aiPicks = 2, added = 1))
        assertEquals("직접 고른 2곳 중에서 골라요", voteReason(cars = 4, aiPicks = 0, added = 2))
    }
}
