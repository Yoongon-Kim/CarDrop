package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 게임 초대: the pop-up only in P and within the minute; driving, the inbox only. */
class GameInviteRulesTest {
    private fun drop(draft: com.cardrop.crew.DropDraft, sentAt: Long = 1_000) = Drop(
        id = "d", fromCarId = "uncle", kind = draft.kind, topic = draft.topic, title = draft.title,
        message = draft.message, createdAtMillis = sentAt, deadlineMillis = sentAt, toCarIds = draft.toCarIds,
    )

    @Test
    fun omokInviteCarriesItsGame() {
        val d = drop(GameInviteRules.omok("game-1", "me"))
        assertEquals("game-1", GameInviteRules.omokGameId(d))
        assertEquals(listOf("me"), d.toCarIds)
        assertEquals(DropKind.SOCIAL, d.kind)
    }

    @Test
    fun quizInviteHasNoGame() {
        val d = drop(GameInviteRules.quiz("상식 퀴즈", emptyList()))
        assertEquals("상식 퀴즈 한 판 할까요?", d.title)
        assertNull(GameInviteRules.omokGameId(d))
    }

    @Test
    fun popsUpOnlyInParkWithinTheMinute() {
        val d = drop(GameInviteRules.quiz("상식 퀴즈", emptyList()), sentAt = 0)
        assertTrue(GameInviteRules.popsUp(d, gamesOpen = true, nowMillis = 1_000))
        assertFalse(GameInviteRules.popsUp(d, gamesOpen = false, nowMillis = 1_000)) // driving: inbox only
        assertFalse(GameInviteRules.popsUp(d, gamesOpen = true, nowMillis = 60_000)) // the minute is over
        val shout = d.copy(topic = DropTopic.MESSAGE)
        assertFalse(GameInviteRules.popsUp(shout, gamesOpen = true, nowMillis = 1_000))
    }
}
