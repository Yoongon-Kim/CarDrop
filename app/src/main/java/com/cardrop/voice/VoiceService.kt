package com.cardrop.voice

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner: lead.
 *
 * Design rule: DECISION drops are spoken aloud; SOCIAL drops are never spoken.
 * The real version uses android.speech.tts.TextToSpeech and SpeechRecognizer if the Pleos image
 * has them (day-1 check), or a cloud service if not.
 */
interface VoiceService {
    /** The last sentence spoken, so the UI can show it as a caption. */
    val lastSpoken: StateFlow<String?>

    fun speak(text: String)

    /** Listens for one sentence. Returns null when nothing was heard. */
    suspend fun listen(): String?
}

class FakeVoiceService : VoiceService {
    private val _lastSpoken = MutableStateFlow<String?>(null)
    override val lastSpoken: StateFlow<String?> = _lastSpoken

    override fun speak(text: String) {
        Log.i("CarDropVoice", "speak: $text")
        _lastSpoken.value = text
    }

    override suspend fun listen(): String? {
        delay(1_200)
        return "배고파"
    }
}
