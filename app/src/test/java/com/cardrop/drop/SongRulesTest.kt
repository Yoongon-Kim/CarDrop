package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The song drop (boards 12, 13): what is sent, how the receiver finds the song, and what plays next. */
class SongRulesTest {
    private val sea = SongRules.SONGS[0] // 바다로 가자
    private val summer = SongRules.SONGS[1] // 여름 고속도로
    private val gangneung = SongRules.SONGS[3] // 강릉 가는 길

    private fun received(draftTopic: DropTopic, message: String) = Drop(
        id = "s1", fromCarId = "uncle", kind = DropKind.SOCIAL, topic = draftTopic,
        title = "", message = message, createdAtMillis = 0, deadlineMillis = 0,
    )

    @Test
    fun theDraftIsASocialSongDropForTheChosenCars() {
        val draft = SongRules.draft(sea, listOf("minji"))
        assertEquals(DropKind.SOCIAL, draft.kind) // on screen only, never spoken
        assertEquals(DropTopic.SONG, draft.topic)
        assertEquals("바다로 가자 · 드라이브 밴드", draft.title)
        assertEquals("sea", draft.message)
        assertEquals(listOf("minji"), draft.toCarIds)
    }

    @Test
    fun theReceiverFindsTheSongAgain() {
        assertEquals(sea, SongRules.songOf(received(DropTopic.SONG, "sea")))
        assertNull(SongRules.songOf(received(DropTopic.SONG, "unknown")))
        assertNull(SongRules.songOf(received(DropTopic.MESSAGE, "sea"))) // a shout is no song
    }

    @Test
    fun nextIsWhatThisCarWasPlaying() {
        assertEquals(summer, SongRules.nextAfter(received = sea, wasPlaying = summer))
        // It was playing that very song: the one after it (board 13's 바다로 가자, then 여름 고속도로).
        assertEquals(summer, SongRules.nextAfter(received = sea, wasPlaying = sea))
        assertEquals(sea, SongRules.nextAfter(received = gangneung, wasPlaying = gangneung)) // wraps around
    }

    @Test
    fun acceptingMakesTheSongPlay() {
        val player = SongPlayer()
        assertEquals(sea, player.nowPlaying.value) // the target's "지금 재생 중"
        assertEquals(sea, player.playNow(gangneung))
        assertEquals(gangneung, player.nowPlaying.value)
    }
}
