package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.crew.DropDraft
import java.util.Base64

/**
 * Owner: Member A (2026-11-15). Board 09's 녹음해서 보내기: the driver's own voice, at most [MAX_SECONDS], sent to the
 * crew as a SOCIAL drop ([DropTopic.VOICE]) with the recording (AAC, about 3 KB a second) Base64 in `message`. Small
 * enough to travel in the crew's database like any drop, so no file storage is needed. The receiver plays it with ▶ on
 * the banner or in the inbox; the app never plays it by itself. Pure Kotlin, unit tested (VoiceClipRulesTest).
 */
object VoiceClipRules {
    const val MAX_SECONDS = 20

    /** The drop for a recording of [seconds] ([audio] = the recorded file's bytes), to [toCarIds] (empty = the whole crew). */
    fun draft(audio: ByteArray, seconds: Int, toCarIds: List<String>): DropDraft = DropDraft(
        kind = DropKind.SOCIAL,
        topic = DropTopic.VOICE,
        title = title(seconds),
        message = Base64.getEncoder().encodeToString(audio),
        toCarIds = toCarIds,
    )

    /** "음성 메시지 · 12초": what the banner and the inbox say about it. */
    fun title(seconds: Int): String = "음성 메시지 · ${seconds.coerceIn(1, MAX_SECONDS)}초"

    /** The recording a drop carries, or null for any other drop (or one that can't be read). */
    fun audioOf(drop: Drop): ByteArray? =
        if (drop.topic != DropTopic.VOICE || drop.message.isBlank()) null
        else runCatching { Base64.getDecoder().decode(drop.message) }.getOrNull()

    /** How many whole seconds [millis] of recording is, at least 1 and at most [MAX_SECONDS]. */
    fun seconds(millis: Long): Int = ((millis + 500) / 1000).toInt().coerceIn(1, MAX_SECONDS)
}
