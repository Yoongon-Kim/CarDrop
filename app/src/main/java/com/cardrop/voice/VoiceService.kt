package com.cardrop.voice

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * One sentence the app said aloud. [count] goes up by one with every sentence, so the same words said
 * twice are two different lines (the caption shows again). A StateFlow ignores a value equal to the
 * one it holds, so without [count] a repeated sentence would be lost.
 */
data class SpokenLine(val text: String, val count: Int)

/** The line after this one: [text] with the next count. */
fun SpokenLine?.next(text: String) = SpokenLine(text, (this?.count ?: 0) + 1)

/**
 * Owner: lead.
 *
 * Design rule: DECISION drops are spoken aloud; SOCIAL drops are never spoken.
 * [PleosVoiceService] is the real version (the Pleos speech SDKs, `cardrop.useRealVoice=true`);
 * Android's own TextToSpeech can't reach the Pleos speech engine (day-1 check, see CLAUDE.md).
 */
interface VoiceService {
    /** The last sentence spoken, so the UI can show it as a caption. Each [speak] makes a new line. */
    val lastSpoken: StateFlow<SpokenLine?>

    /**
     * True while the microphone is listening: from [listen] until a sentence is heard, nothing is
     * heard, or the time limit ends it. The UI shows a "듣고 있어요…" caption meanwhile.
     */
    val listening: StateFlow<Boolean>

    fun speak(text: String)

    /**
     * Listens for one sentence. Returns null when nothing was heard.
     * [placeName] = true when we expect only a place name (board 10 "말해서 추가"). The fake then
     * hears a place name; the Pleos recognizer has no special mode for it.
     */
    suspend fun listen(placeName: Boolean = false): String?
}

class FakeVoiceService : VoiceService {
    private val _lastSpoken = MutableStateFlow<SpokenLine?>(null)
    override val lastSpoken: StateFlow<SpokenLine?> = _lastSpoken

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening

    override fun speak(text: String) {
        Log.i("CarDropVoice", "speak: $text")
        _lastSpoken.update { it.next(text) }
    }

    override suspend fun listen(placeName: Boolean): String? {
        _listening.value = true
        try {
            delay(1_200)
        } finally {
            _listening.value = false
        }
        return if (placeName) "평창휴게소" else "배고파"
    }
}
