package com.cardrop.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.*
import com.cardrop.drop.VoteRules
import com.cardrop.voice.VoiceService

/** Board 14: display the final route and report whether guidance really started. */
@Composable
fun DecidedScreen(vm: DriveViewModel, dropId: String, onDone: () -> Unit, voice: VoiceService? = null) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val route by vm.navRoute.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    LaunchedEffect(dropId) { vm.clearError() }
    val drop = drops.firstOrNull { it.id == dropId }
    val winner = drop?.decidedCandidate
    ScreenColumn {
        if (drop == null || winner == null) {
            ScreenHeader("크루의 결정", if (drop == null) "결정 내용을 찾을 수 없어요" else "결정을 기다리고 있어요")
            SecondaryButton("주행 화면으로", onDone)
            return@ScreenColumn
        }
        val ids = drop.voterIds(crew?.cars?.map { it.id }.orEmpty())
        ScreenHeader("투표가 끝났어요 · ${ids.size}대 중 ${VoteRules.agreeing(drop, ids)}대 동의", "${winner.place.name}로 결정되었어요")
        CdCard(Modifier.fillMaxWidth()) {
            InfoRow(winner.place.name, winner.reason)
            Text(route.lastAction ?: "현재 경로를 확인하고 있어요", style = MaterialTheme.typography.bodyLarge)
            if (vm.skipped(drop.id)) StatusText("우리 차는 이 경유지에 들르지 않아요")
        }
        Pill(input.label)
        DriveActionFeedback(action)
        val start = { if (vm.startGuidance()) onDone() }
        val skip = { if (vm.skipStop(drop.id)) onDone() }
        val ready = crew != null && !action.busy
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("지금 안내 시작", start, Modifier.weight(1f), ready && input.allowsTouch)
            SecondaryButton("우리 차는 안 들르기", skip, Modifier.weight(1f), ready && input.allowsTouch)
        }
        DriveVoiceReply(voice, "“안내 시작” 또는 “우리 차는 안 들르기”라고 말해 주세요", ready,
            setOf(DriveVoiceCommand.START, DriveVoiceCommand.SKIP)) {
            if (it == DriveVoiceCommand.START) start() else skip()
        }
    }
}
