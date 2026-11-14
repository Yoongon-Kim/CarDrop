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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drop.VoteRules
import com.cardrop.drop.voteTag
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

/**
 * Board 14, as in the design: a blue result card ("{place}로 결정되었어요"), guidance that starts by itself after
 * 3 seconds unless the driver picks "우리 차는 안 들르기", and when each car reaches the stop ("도착 예정").
 * A new destination (목적지 변경) has no "우리 차는 안 들르기": it is no stop on the way, and skipping it would not
 * bring the old destination back.
 *
 * The countdown runs only with the in-app navigator ([DriveViewModel.autoStartsGuidance]). Once it has started
 * guidance the main button says "주행 화면으로".
 *
 * The screen is a notice, not a page to stay on (lead, 2026-11-14): 15 seconds after it opened it goes back to 홈
 * by itself, as "주행 화면으로" does, so the driver doesn't have to tap. If the microphone is listening then (the
 * driver may be saying "우리 차는 안 들르기"), it waits until the microphone stops.
 *
 * Its first line says whose vote it was ([voteEndedText]): it opens on every car the vote reroutes, not only on
 * the car that sent it.
 */
@Composable
fun DecidedScreen(vm: DriveViewModel, dropId: String, onDone: () -> Unit, voice: VoiceService? = null) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val route by vm.navRoute.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    LaunchedEffect(dropId) { vm.clearError() }
    // The 15-second timer. It stops by itself when the screen closes earlier (a tap, or another screen opening).
    val close by rememberUpdatedState(onDone)
    LaunchedEffect(dropId) {
        delay(AUTO_CLOSE_MILLIS)
        voice?.listening?.first { listening -> !listening }
        close()
    }
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
        // What changed in the navigator, in the target's words (line 493); the navigator's toast keeps its own.
        // When the place isn't in the route (e.g. "…는 이미 지났어요"), the navigator's last message says why.
        val destinationVote = drop.topic == DropTopic.DESTINATION
        val inRoute = if (destinationVote) route.destination?.id == winner.place.id else route.waypoint?.id == winner.place.id
        val fixed = if (destinationVote) "왼쪽 내비의 목적지를 바꿨어요" else "왼쪽 내비의 경유지를 바꿨어요"
        val changed = if (inRoute) fixed else route.lastAction ?: fixed
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
                    voteEndedText(drop, crew?.cars.orEmpty(), vm.myCarId),
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
                // A new destination is no stop to leave out: skipStop would only keep driving to it.
                if (!destinationVote) {
                    PrimaryButton(
                        "우리 차는 안 들르기", skip, Modifier.weight(1f), ready && !skipped,
                        containerColor = CdColors.White.copy(alpha = 0.2f),
                        height = 58.dp, radius = 16.dp, fontSize = 15.sp, fontWeight = FontWeight.Bold, horizontalPadding = 8.dp,
                    )
                }
            }
        }

        // drops: the crew's "도착했어요" / "지나쳤어요" messages change the list too.
        val arrivals = remember(drop, drops, positions, route, crew, skipped) { vm.arrivals(drop) }
        if (arrivals.isNotEmpty()) {
            // Padding 16 x 22 and no gap: the rows bring their own space and lines (target lines 497-499).
            CdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp), gap = 0.dp) {
                Text(
                    "도착 예정",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CdColors.Ink,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                arrivals.forEach { ArrivalRow(it) }
            }
        }
        DriveActionFeedback(action)
        if (destinationVote) {
            DriveVoiceReply(voice, "“안내 시작”이라고 말해 주세요", ready, setOf(DriveVoiceCommand.START)) { start() }
        } else {
            DriveVoiceReply(voice, "“안내 시작” 또는 “우리 차는 안 들르기”라고 말해 주세요", ready,
                setOf(DriveVoiceCommand.START, DriveVoiceCommand.SKIP)) {
                if (it == DriveVoiceCommand.START) start() else skip()
            }
        }
    }
}

/**
 * One car in "도착 예정": the car, its name with its status right after it in smaller grey text, and when it gets
 * there; 6 above and below, with a thin line under every row (target line 499).
 */
@Composable
private fun ArrivalRow(arrival: Arrival) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val line = 1.dp.toPx()
                drawLine(CdColors.Gray100, Offset(0f, size.height - line / 2), Offset(size.width, size.height - line / 2), line)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CarBadge(arrival.car)
        Text(
            buildAnnotatedString {
                append(arrival.car.name)
                append(" ")
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CdColors.Gray600)) {
                    append(arrival.status)
                }
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CdColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(arrival.time, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
    }
}

/**
 * Board 14's first line: whose vote ended, about what, and how many cars agreed, e.g. "삼촌 차의 휴게소 투표가
 * 끝났어요 · 4대 중 3대 동의". This car's own vote says "우리 차", as board 11's tag does; a car no longer in the crew
 * is "일행". Only the cars the vote was for are counted, without a quick drop's sender (it doesn't vote); a quick drop
 * with no other car to vote (this car alone in the crew) has no count. The design says just "투표가 끝났어요 · 4대 중
 * 3대 동의", which made another car's result look like this car's own (lead, 2026-11-15). Pure Kotlin, unit tested
 * (VoteEndedTextTest).
 */
internal fun voteEndedText(drop: Drop, cars: List<Car>, myCarId: String): String {
    val sender = if (drop.fromCarId == myCarId) "우리 차" else cars.firstOrNull { it.id == drop.fromCarId }?.name ?: "일행"
    val ids = drop.voterIds(cars.map { it.id })
    val ended = "${sender}의 ${drop.topic.voteTag()} 투표가 끝났어요"
    return if (ids.isEmpty()) ended else "$ended · ${ids.size}대 중 ${VoteRules.agreeing(drop, ids)}대 동의"
}

private const val COUNTDOWN_MILLIS = 3_000

/** How long board 14 stays before it goes back to 홈 by itself. */
private const val AUTO_CLOSE_MILLIS = 15_000L
