package com.cardrop.voice

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner: lead.
 *
 * Design rule: DECISION drops are spoken aloud; SOCIAL drops are never spoken.
 * [PleosVoiceService] is the real version (the Pleos speech SDKs, `cardrop.useRealVoice=true`);
 * Android's own TextToSpeech can't reach the Pleos speech engine (day-1 check, see CLAUDE.md).
 */
interface VoiceService {
    /** The last sentence spoken, so the UI can show it as a caption. */
    val lastSpoken: StateFlow<String?>

    fun speak(text: String)

    /**
     * Listens for one sentence. Returns null when nothing was heard.
     * [placeName] = true when we expect only a place name (board 10 "말해서 추가"). The fake then
     * hears a place name; the Pleos recognizer has no special mode for it.
     */
    suspend fun listen(placeName: Boolean = false): String?
}

class FakeVoiceService : VoiceService {
    private val _lastSpoken = MutableStateFlow<String?>(null)
    override val lastSpoken: StateFlow<String?> = _lastSpoken

    override fun speak(text: String) {
        Log.i("CarDropVoice", "speak: $text")
        _lastSpoken.value = text
    }

    override suspend fun listen(placeName: Boolean): String? {
        delay(1_200)
        return if (placeName) "평창휴게소" else "배고파"
    }
}
