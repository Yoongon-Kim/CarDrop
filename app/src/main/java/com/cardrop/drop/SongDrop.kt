package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.crew.DropDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner: Member A. The song drop (boards 12 and 13): a SOCIAL drop, on screen only and never spoken.
 * Nothing really plays: this demo has no Apple Music, so [SongPlayer] only remembers what "plays" now.
 */
data class Song(val id: String, val name: String, val artist: String, val length: String)

/** The song-drop rules. Plain Kotlin, so SongRulesTest covers them. */
object SongRules {
    /**
     * Board 12's four songs (target line 729). The target gives only 바다로 가자's length (3:42, board 13);
     * the other lengths are made up for the demo.
     */
    val SONGS = listOf(
        Song("sea", "바다로 가자", "드라이브 밴드", "3:42"),
        Song("summer", "여름 고속도로", "파란 하늘", "3:18"),
        Song("blues", "휴게소 블루스", "호두과자 트리오", "4:05"),
        Song("gangneung", "강릉 가는 길", "동해선", "3:56"),
    )

    /** The playlist board 13 names after accepting ("Apple Music · 강릉 가는 길 재생목록"). */
    const val PLAYLIST = "강릉 가는 길"

    /**
     * The drop for [song]. The title is readable by itself (the inbox, a build that doesn't know songs);
     * the song's id travels in `message`, so the receiver finds the song again ([songOf]).
     */
    fun draft(song: Song, toCarIds: List<String>): DropDraft = DropDraft(
        kind = DropKind.SOCIAL,
        topic = DropTopic.SONG,
        title = "${song.name} · ${song.artist}",
        message = song.id,
        toCarIds = toCarIds,
    )

    /** The song a drop carries, or null for any other drop (or a song this build doesn't know). */
    fun songOf(drop: Drop): Song? =
        if (drop.topic == DropTopic.SONG) SONGS.firstOrNull { it.id == drop.message } else null

    /**
     * Board 13's 다음 after accepting [received]: the song this car was playing ("원래 듣던 곡 이어서"). If it was
     * playing that very song, the one after it in the list.
     */
    fun nextAfter(received: Song, wasPlaying: Song): Song =
        if (wasPlaying.id != received.id) wasPlaying
        else SONGS[(SONGS.indexOfFirst { it.id == received.id } + 1).mod(SONGS.size)]
}

/**
 * What this car "plays" now (no sound). Board 12 shows it as "내 차에서 지금 재생 중"; accepting a song on board 13
 * makes that song the one playing. Starts on the first song, as in the target. AppContainer keeps one.
 */
class SongPlayer {
    private val _nowPlaying = MutableStateFlow(SongRules.SONGS.first())
    val nowPlaying: StateFlow<Song> = _nowPlaying.asStateFlow()

    /** Plays [song] now and returns what comes next (board 13's 다음). */
    fun playNow(song: Song): Song {
        val next = SongRules.nextAfter(song, _nowPlaying.value)
        _nowPlaying.value = song
        return next
    }
}
