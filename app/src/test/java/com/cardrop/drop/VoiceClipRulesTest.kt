package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 09's 녹음해서 보내기: the recording travels in the drop and comes back the same. */
class VoiceClipRulesTest {
    private fun drop(topic: DropTopic, message: String) = Drop(
        id = "v", fromCarId = "uncle", kind = DropKind.SOCIAL, topic = topic, title = "t", message = message,
        createdAtMillis = 0, deadlineMillis = 0,
    )

    @Test
    fun theRecordingComesBackTheSame() {
        val audio = ByteArray(3_000) { (it * 7).toByte() }
        val draft = VoiceClipRules.draft(audio, seconds = 12, toCarIds = emptyList())
        assertEquals("음성 메시지 · 12초", draft.title)
        assertEquals(DropKind.SOCIAL, draft.kind)
        assertArrayEquals(audio, VoiceClipRules.audioOf(drop(DropTopic.VOICE, draft.message)))
    }

    @Test
    fun otherDropsCarryNoRecording() {
        assertNull(VoiceClipRules.audioOf(drop(DropTopic.MESSAGE, "aGVsbG8=")))
        assertNull(VoiceClipRules.audioOf(drop(DropTopic.VOICE, "")))
        assertNull(VoiceClipRules.audioOf(drop(DropTopic.VOICE, "not base64 !!")))
    }

    @Test
    fun secondsStayBetweenOneAndTwenty() {
        assertEquals(1, VoiceClipRules.seconds(200))
        assertEquals(12, VoiceClipRules.seconds(11_600))
        assertEquals(20, VoiceClipRules.seconds(21_000))
    }
}
