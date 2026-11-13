package com.cardrop.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drop.VoteRules
import com.cardrop.voice.VoiceService
import kotlin.math.ceil

/**
 * Board 14, as in the design: a blue result card ("{place}로 결정되었어요"), guidance that starts by itself after
 * 3 seconds unless the driver picks "우리 차는 안 들르기", and when each car reaches the stop ("도착 예정").
 *
 * The countdown runs only with the in-app navigator ([DriveViewModel.autoStartsGuidance]). Once it has started
 * guidance the screen stays, so the arrivals can still be read; the main button then says "주행 화면으로".
 */
@Composable
fun DecidedScreen(vm: DriveViewModel, dropId: String, onDone: () -> Unit, voice: VoiceService? = null) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val route by vm.navRoute.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
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
        val skipped = vm.skipped(drop.id)
        val ready = crew != null && !action.busy
        val countdown = vm.autoStartsGuidance && crew != null && !skipped
        var started by remember(dropId) { mutableStateOf(false) }
        val progress = remember(dropId) { Animatable(0f) }
        LaunchedEffect(dropId, countdown) {
            if (!countdown || started) return@LaunchedEffect
            progress.animateTo(1f, tween(COUNTDOWN_MILLIS, easing = LinearEasing))
            if (vm.startGuidance()) started = true
        }
        // A tap means "go now": start guidance (unless the countdown already did) and go back to driving.
        val start = { if (started || vm.startGuidance()) onDone() }
        val skip = { if (vm.skipStop(drop.id)) onDone() }
        val ids = drop.voterIds(crew?.cars?.map { it.id }.orEmpty())
        val changed = route.lastAction ?: "왼쪽 내비의 경유지를 바꿨어요"
        val status = when {
            skipped -> "우리 차는 이 경유지에 들르지 않아요"
            countdown && !started -> {
                val seconds = ceil((1f - progress.value) * COUNTDOWN_MILLIS / 1000f).toInt().coerceAtLeast(1)
                "$changed · ${seconds}초 후 안내 시작"
            }
            else -> changed
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(CdColors.Blue).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CdColors.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CdIcons.Check, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    "투표가 끝났어요 · ${ids.size}대 중 ${VoteRules.agreeing(drop, ids)}대 동의",
                    color = CdColors.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${winner.place.name.withRo()}\n결정되었어요",
                color = CdColors.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 45.sp,
            )
            Text(status, color = CdColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (countdown) {
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CdColors.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (started) 1f else progress.value).background(CdColors.White))
                }
            }
            // 70% of the card wide, 2 : 1, 58 high with corners 16 (target line 495). The CSS buttons have almost
            // no side padding, so "우리 차는 안 들르기" fits in the smaller one.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(0.7f)) {
                PrimaryButton(
                    if (started) "주행 화면으로" else "지금 안내 시작", start, Modifier.weight(2f), ready,
                    containerColor = CdColors.White, contentColor = CdColors.BlueDark,
                    height = 58.dp, radius = 16.dp, fontSize = 17.sp, horizontalPadding = 8.dp,
                )
                PrimaryButton(
                    "우리 차는 안 들르기", skip, Modifier.weight(1f), ready && !skipped,
                    containerColor = CdColors.White.copy(alpha = 0.2f),
                    height = 58.dp, radius = 16.dp, fontSize = 15.sp, fontWeight = FontWeight.Bold, horizontalPadding = 8.dp,
                )
            }
        }

        // drops: the crew's "도착했어요" / "지나쳤어요" messages change the list too.
        val arrivals = remember(drop, drops, positions, route, crew, skipped) { vm.arrivals(drop) }
        if (arrivals.isNotEmpty()) {
            CdCard(Modifier.fillMaxWidth()) {
                Text("도착 예정", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
                arrivals.forEach { ArrivalRow(it) }
            }
        }
        DriveActionFeedback(action)
        DriveVoiceReply(voice, "“안내 시작” 또는 “우리 차는 안 들르기”라고 말해 주세요", ready,
            setOf(DriveVoiceCommand.START, DriveVoiceCommand.SKIP)) {
            if (it == DriveVoiceCommand.START) start() else skip()
        }
    }
}

/** One car in "도착 예정": the car, its name and status, and when it gets there. */
@Composable
private fun ArrivalRow(arrival: Arrival) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarBadge(arrival.car)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(arrival.car.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
            Text(arrival.status, fontSize = 13.sp, color = CdColors.Gray600)
        }
        Text(arrival.time, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
    }
}

private const val COUNTDOWN_MILLIS = 3_000
