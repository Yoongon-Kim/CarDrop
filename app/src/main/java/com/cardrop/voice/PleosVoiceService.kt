package com.cardrop.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * Owner: lead. The real [VoiceService] on Pleos, in Korean, through the Pleos TextToSpeech and
 * SpeechToText SDKs (docs: document.pleos.ai > Connect SDK > TextToSpeech / SpeechToText). They
 * talk to the car's speech service (ai.pleos.playground.caas), which also records the microphone.
 * Android's own TextToSpeech can't use that service: Android's engine lookup skips it.
 *
 * On-device mode, so no app registration is needed. HYBRID or SERVER mode (better quality, needs
 * internet) would need Pleos Playground app credentials through `registerApp(...)`.
 *
 * This starts with the app, so nothing here may throw: if the Pleos service fails, CarDrop still
 * shows the captions, just without sound. Turned on with `cardrop.useRealVoice=true` in
 * local.properties. Logs: `adb logcat -s CarDropVoice`.
 */
class PleosVoiceService(context: Context) : VoiceService {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var encouragementId: Long? = null
    @Volatile private var encouragementCaptionId: Long? = null
    private var encouragementFocus: AudioFocusRequest? = null
    private var ignoreEndUntil = 0L

    private val main = Handler(Looper.getMainLooper())

    private val _lastSpoken = MutableStateFlow<SpokenLine?>(null)
    override val lastSpoken: StateFlow<SpokenLine?> = _lastSpoken

    private val _listening = MutableStateFlow(false)
    override val listening: StateFlow<Boolean> = _listening

    // Speaking. The queue and these flags are only touched on the main thread.
    private val speaker: TextToSpeech? = attempt("create text-to-speech") {
        TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    }
    private var speakerReady = false
    private var speaking = false
    private val queue = ArrayDeque<String>()
    /** In case the engine never reports the end of a sentence: go on after [MAX_SPEAK_MS]. */
    private val unstick = Runnable {
        Log.w(TAG, "no end signal from the speech engine, going on")
        finishedSpeaking()
    }

    // Listening.
    private val recognizer: SpeechToText? = attempt("create speech recognition") {
        SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    }
    @Volatile private var recognizerReady = false

    private val speakerEvents = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            Log.d(TAG, "speaking")
        }

        override fun onDone() {
            Log.d(TAG, "spoke")
            main.post { finishedSpeaking() }
        }

        override fun onStop() {
            main.post { finishedSpeaking() }
        }

        override fun onError(errorType: TtsError) {
            Log.w(TAG, "could not speak: $errorType")
            main.post { finishedSpeaking() }
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        // The SDKs must be initialized before any other call (Pleos docs).
        attempt("start text-to-speech") {
            speaker?.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    main.post { onSpeakerReady() }
                }

                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "text-to-speech did not start: $errorType")
                }
            })
        }
        attempt("start speech recognition") {
            recognizer?.initialize(object : SttInitListener {
                override fun onSuccess() {
                    attempt("set the recognition language") { recognizer.setLocale(Locale.KOREA) } ?: return
                    recognizerReady = true
                    Log.d(TAG, "speech recognition ready (Korean, on-device)")
                }

                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "speech recognition did not start: $errorType")
                }
            })
        }
    }

    private fun onSpeakerReady() {
        val speaker = speaker ?: return
        attempt("set up text-to-speech") {
            speaker.addEventListener(speakerEvents)
            speaker.setLocale(Locale.KOREA)
        } ?: return
        Log.d(TAG, "text-to-speech ready (Korean, on-device)")
        speakerReady = true
        speakNext()
    }

    /** Says [text] after anything already being said, and shows it as a caption. */
    override fun speak(text: String) {
        encouragementCaptionId = null
        _lastSpoken.update { it.next(text) } // the caption shows even when there is no sound
        main.post {
            queue.addLast(text)
            if (encouragementId != null) stopEncouragement() else speakNext()
        }
    }

    override fun trySpeakEncouragement(text: String, entryId: Long, deadlineMillis: Long): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper() || !speakerReady || speaking || queue.isNotEmpty() ||
            _listening.value || SystemClock.elapsedRealtime() > deadlineMillis || audio.mode != AudioManager.MODE_NORMAL) return false
        val navigationPlaying = runCatching { audio.activePlaybackConfigurations.any { it.audioAttributes.usage in setOf(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.USAGE_ASSISTANT,
        ) } }.getOrDefault(true)
        if (navigationPlaying) return false
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0) cancelEncouragement(entryId) }.build()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        encouragementFocus = focus; encouragementId = entryId; speaking = true
        encouragementCaptionId = entryId
        _lastSpoken.update { it.next(text) }
        main.postDelayed(unstick, MAX_SPEAK_MS)
        if (attempt("speak encouragement") { speaker?.speak(text, true) } == null) { finishedSpeaking(); return false }
        return true
    }

    override fun cancelEncouragement(entryId: Long) {
        main.post {
            if (encouragementCaptionId == entryId) { encouragementCaptionId = null; _lastSpoken.value = null }
            if (encouragementId == entryId) stopEncouragement()
        }
    }

    private fun stopEncouragement() {
        encouragementId = null
        encouragementFocus?.let { audio.abandonAudioFocusRequest(it) }; encouragementFocus = null
        ignoreEndUntil = SystemClock.elapsedRealtime() + 150
        attempt("stop encouragement") { speaker?.stop() }
        speaking = false; main.removeCallbacks(unstick)
        main.postDelayed({ speakNext() }, 175)
    }

    private fun speakNext() {
        val speaker = speaker ?: return
        if (!speakerReady || speaking) return
        val text = queue.removeFirstOrNull() ?: return
        speaking = true
        main.postDelayed(unstick, MAX_SPEAK_MS)
        attempt("speak") { speaker.speak(text, true) } // true: this text is the whole request
            ?: finishedSpeaking()
    }

    private fun finishedSpeaking() {
        if (SystemClock.elapsedRealtime() < ignoreEndUntil) return
        encouragementId = null
        encouragementFocus?.let { audio.abandonAudioFocusRequest(it) }; encouragementFocus = null
        main.removeCallbacks(unstick)
        speaking = false
        speakNext()
    }

    /**
     * Listens for one sentence in Korean. Returns null when nothing was heard, when recognition
     * isn't ready, or after [LISTEN_TIMEOUT_MS]. The Pleos recognizer has no "place name" mode,
     * so [placeName] changes nothing here.
     */
    override suspend fun listen(placeName: Boolean): String? {
        val recognizer = recognizer
        if (recognizer == null || !recognizerReady) {
            Log.w(TAG, "speech recognition is not ready")
            return null
        }
        main.post {
            queue.clear() // don't let the microphone hear our own voice
            if (encouragementId != null) stopEncouragement() else attempt("stop speaking") { speaker?.stop() }
        }
        _listening.value = true
        return try {
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) { recognizeOnce(recognizer) }
                .also { Log.d(TAG, if (it == null) "nothing heard" else "heard: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The 04 voice button has no error handling of its own, so never throw from here.
            Log.w(TAG, "could not listen", e)
            null
        } finally {
            _listening.value = false
        }
    }

    /** One recognition session: the final text, or the last partial text if it ends without one. */
    private suspend fun recognizeOnce(recognizer: SpeechToText): String? = suspendCancellableCoroutine { cont ->
        val listener = object : ResultListener {
            private var latest: String? = null
            private var done = false

            private fun finish(text: String?) {
                synchronized(this) {
                    if (done) return
                    done = true
                }
                attempt("remove the recognition listener") { recognizer.removeListener(this) }
                if (cont.isActive) cont.resume(text?.trim()?.takeIf { it.isNotEmpty() })
            }

            override fun onReady() = Unit
            override fun onStartedRecognition() {
                Log.d(TAG, "listening")
            }

            override fun onUpdated(stt: String, completed: Boolean) {
                latest = stt
                if (completed) finish(stt)
            }

            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit

            override fun onEndedRecognition() {
                // Pleos sends the final sentence a few ms *after* this (seen on the emulator), so wait
                // briefly for it; without one, use the last partial text.
                main.postDelayed({ finish(latest) }, FINAL_TEXT_WAIT_MS)
            }

            override fun onError(errorType: SttError) {
                Log.w(TAG, "speech recognition error: $errorType")
                finish(null)
            }
        }
        recognizer.addListener(listener) // listener first, then request() (Pleos docs)
        // Closing the screen or the time limit ends the session and frees the microphone.
        cont.invokeOnCancellation {
            main.post {
                attempt("stop listening") {
                    recognizer.stop()
                    recognizer.removeListener(listener)
                }
            }
        }
        recognizer.request()
    }

    /** Runs [block]; on any exception logs it and returns null instead of crashing the app. */
    private inline fun <T> attempt(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "could not $what", e)
            null
        }

    private companion object {
        const val TAG = "CarDropVoice"
        const val LISTEN_TIMEOUT_MS = 15_000L
        const val FINAL_TEXT_WAIT_MS = 1_000L
        const val MAX_SPEAK_MS = 20_000L
    }
}
