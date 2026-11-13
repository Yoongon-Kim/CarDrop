package com.cardrop.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.StatusText
import com.cardrop.voice.FakeVoiceService
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DriveActionFeedback(state: DriveActionState) {
    state.message?.let { StatusText(it) }
    state.error?.let { ErrorText(it) }
}

/**
 * Board 14's "말로 응답하기": the [hint] and a button that listens once for one of the [accepted] commands. Only with
 * the real voice: the target draws no voice part on 14 (lines 488-501), and with the fake voice (which always hears
 * "배고파") there is nothing to say, so the driver answers with the buttons and nothing shows here.
 */
@Composable
internal fun DriveVoiceReply(
    voice: VoiceService?,
    hint: String,
    enabled: Boolean,
    accepted: Set<DriveVoiceCommand>,
    onCommand: (DriveVoiceCommand) -> Unit,
) {
    if (voice == null || voice is FakeVoiceService) return
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentCommand by rememberUpdatedState(onCommand)
    val canReply by rememberUpdatedState(enabled)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hint)
        val microphoneBusy by voice.listening.collectAsStateWithLifecycle()
        PrimaryButton(if (listening) "듣고 있어요…" else "말로 응답하기", enabled = enabled && !listening && !microphoneBusy, onClick = {
            if (listening || voice.listening.value) return@PrimaryButton
            listening = true
            error = null
            scope.launch {
                try {
                    val command = driveVoiceCommand(voice.listen())
                    if (!canReply) return@launch
                    if (command != null && command in accepted) currentCommand(command)
                    else error = "응답을 확인하지 못했어요. $hint"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "음성을 듣지 못했어요. 다시 시도해 주세요"
                } finally {
                    listening = false
                }
            }
        })
        error?.let { ErrorText(it) }
    }
}

/**
 * A small "말로 답하기" chip for answering by voice inside a banner (board 06). It listens once; a reply it
 * can't match changes nothing, and the chip says "다시 말해 주세요" for a moment.
 */
@Composable
internal fun VoiceAnswerChip(
    voice: VoiceService,
    enabled: Boolean,
    accepted: Set<DriveVoiceCommand>,
    onCommand: (DriveVoiceCommand) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(VOICE_IDLE) }
    val microphoneBusy by voice.listening.collectAsStateWithLifecycle()
    val currentCommand by rememberUpdatedState(onCommand)
    val canReply by rememberUpdatedState(enabled)
    CdChip(
        text = label,
        onClick = {
            if (voice.listening.value) return@CdChip
            label = "듣고 있어요…"
            scope.launch {
                val command = try {
                    driveVoiceCommand(voice.listen())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (canReply && command != null && command in accepted) {
                    label = VOICE_IDLE
                    currentCommand(command)
                } else {
                    label = "다시 말해 주세요"
                    delay(3_000)
                    label = VOICE_IDLE
                }
            }
        },
        style = ChipStyle.White,
        icon = CdIcons.Mic,
        enabled = enabled && !microphoneBusy && label == VOICE_IDLE,
    )
}

private const val VOICE_IDLE = "말로 답하기"
