package com.cardrop.drop

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.cardrop.core.model.Drop
import java.io.File

/** A finished recording: its bytes and length. */
class VoiceRecording(val audio: ByteArray, val seconds: Int)

/**
 * Records the microphone with Android's own [MediaRecorder] into the app's cache: mono AAC, 16 kHz, 24 kbit/s, and
 * never longer than [VoiceClipRules.MAX_SECONDS] ([onLimit] then runs). Needs RECORD_AUDIO.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private val file get() = File(context.cacheDir, "voice-out.m4a")

    val recording: Boolean get() = recorder != null

    /** Starts recording. False when the microphone can't be opened (e.g. in use, or no permission). */
    fun start(onLimit: () -> Unit): Boolean {
        stopQuietly()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(24_000)
            r.setMaxDuration(VoiceClipRules.MAX_SECONDS * 1_000)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            r.prepare()
            r.start()
            recorder = r
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start recording: $e")
            r.release()
            false
        }
    }

    /** Stops and returns the recording, or null when nothing usable was recorded. */
    fun stop(): VoiceRecording? {
        val r = recorder ?: return null
        recorder = null
        val millis = System.currentTimeMillis() - startedAt
        return try {
            r.stop()
            VoiceRecording(file.readBytes(), VoiceClipRules.seconds(millis))
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop recording: $e") // e.g. stopped right after starting
            null
        } finally {
            r.release()
        }
    }

    /** Throws away a recording in progress (e.g. the screen closed). */
    fun stopQuietly() {
        recorder?.let { r -> runCatching { r.stop() }; r.release() }
        recorder = null
    }

    private companion object {
        const val TAG = "CarDropVoiceMsg"
    }
}

/** Plays a voice message with Android's own [MediaPlayer]; one at a time, a new one stops the last. */
object VoicePlayer {
    private var player: MediaPlayer? = null

    /** Plays [drop]'s recording. False when it carries none. [onDone] runs when it ends. */
    fun play(context: Context, drop: Drop, onDone: () -> Unit = {}): Boolean =
        VoiceClipRules.audioOf(drop)?.let { play(context, it, onDone) } ?: false

    /** Plays [audio] (a recording's bytes), e.g. board 09's 들어보기 before sending. */
    fun play(context: Context, audio: ByteArray, onDone: () -> Unit = {}): Boolean {
        stop()
        return try {
            val file = File(context.cacheDir, "voice-in.m4a").apply { writeBytes(audio) }
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop(); onDone() }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("CarDropVoiceMsg", "Could not play a voice message: $e")
            stop()
            false
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}
