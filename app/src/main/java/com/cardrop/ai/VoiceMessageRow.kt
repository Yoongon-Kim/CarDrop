package com.cardrop.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drop.VoiceClipRules
import com.cardrop.drop.VoicePlayer
import com.cardrop.drop.VoiceRecorder
import com.cardrop.drop.VoiceRecording
import kotlinx.coroutines.delay

/**
 * Board 09 녹음해서 보내기 (Member A, 2026-11-15): tap to record the driver's own voice (at most 20 s, asks for the
 * microphone the first time), tap again to stop, then 들어보기 / 다시 녹음 / 보내기. [onSend] sends it as a voice
 * message (`AiViewModel.sendVoice`). [enabled] = false while the other microphone row is listening: one at a time.
 */
@Composable
fun VoiceMessageRow(enabled: Boolean, onSend: (VoiceRecording) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf<VoiceRecording?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { recorder.stopQuietly(); VoicePlayer.stop() } }

    fun stop() {
        recording = false
        recorded = recorder.stop()
        if (recorded == null) problem = "녹음이 너무 짧았어요. 다시 녹음해 주세요"
    }
    fun start() {
        problem = null
        recorded = null
        elapsed = 0
        VoicePlayer.stop()
        recording = recorder.start(onLimit = { stop() })
        if (!recording) problem = "마이크를 열 수 없어요. 잠시 뒤 다시 해 주세요"
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else problem = "마이크 권한이 있어야 녹음할 수 있어요"
    }
    // The seconds while recording.
    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            elapsed = (elapsed + 1).coerceAtMost(VoiceClipRules.MAX_SECONDS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(if (recording) CdColors.RedLight else CdColors.VioletLight)
                .clickable(enabled = enabled, role = Role.Button) {
                    when {
                        recording -> stop()
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> start()
                        else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(if (recording) CdColors.Alert else CdColors.Violet),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Mic, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (recording) "녹음 중 · 0:%02d / 0:%02d".format(elapsed, VoiceClipRules.MAX_SECONDS) else "녹음해서 보내기",
                    color = if (recording) CdColors.Red else CdColors.VioletInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (recording) "다시 누르면 녹음을 끝내요" else "내 목소리를 그대로 보내요 · 최대 ${VoiceClipRules.MAX_SECONDS}초",
                    color = CdColors.Gray600,
                    fontSize = 14.sp,
                )
            }
        }
        recorded?.let { clip ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    VoiceClipRules.title(clip.seconds),
                    color = CdColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                CdChip("▶ 들어보기", onClick = { VoicePlayer.play(context, clip.audio) }, style = ChipStyle.White)
                CdChip("다시 녹음", onClick = { recorded = null }, style = ChipStyle.White)
                CdChip("보내기", onClick = { VoicePlayer.stop(); onSend(clip) }, style = ChipStyle.PrimaryViolet)
            }
        }
        problem?.let { ErrorText(it) }
    }
}
