package com.cardrop.drop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel
import kotlinx.coroutines.delay

/**
 * Board 07 드랍 편지함 · Owner: Member A.
 * A half sheet that slides in from the right over board 04 (the drive screen stays visible behind a
 * dim layer). Tapping the dim layer or ✕ closes it. Tabs: 전체 / 응답 필요 / 소셜 / 지난 결정.
 * [isSkipped]: stops this car turned down (board 06's 가던 길 갈래요), so they don't wait for an answer.
 */
@Composable
fun InboxScreen(
    vm: CrewViewModel,
    onClose: () -> Unit,
    isSkipped: (dropId: String) -> Boolean = { false },
    onOpenDrop: (Drop) -> Unit,
) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(InboxTab.ALL) }

    // Clock for "3분 전"; ticks every 15 s, enough for minute labels.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    // Slide in from the right (design: cdSlide 0.35 s ease-out). 1 = off screen, 0 = in place.
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }

    // Only drops this car takes part in (board 08 can send to some cars only), newest first.
    val mine = drops.filter { it.isFor(vm.myCarId) }.asReversed()
    val shown = mine.filter { tab.matches(it, vm.myCarId, isSkipped) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dim layer over the drive screen. A tap here closes the sheet.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - slide.value }
                .background(CdColors.Ink.copy(alpha = 0.32f))
                .noRippleClick(onClose),
        )
        Sheet(slide.value) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("드랍 편지함", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CdColors.White)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CdIcons.Close, contentDescription = "닫기", tint = CdColors.Ink2, modifier = Modifier.size(22.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InboxTab.entries.forEach { t ->
                    val count = mine.count { t.matches(it, vm.myCarId, isSkipped) }
                    CdChip(
                        text = if (t == InboxTab.NEEDS_ANSWER && count > 0) "${t.label} $count" else t.label,
                        onClick = { tab = t },
                        style = if (t == tab) ChipStyle.Primary else ChipStyle.White,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CdColors.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (shown.isEmpty()) {
                    Text(
                        if (mine.isEmpty()) "아직 받은 드랍이 없어요" else "여기에 해당하는 드랍이 없어요",
                        fontSize = 15.sp,
                        color = CdColors.Gray600,
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
                }
                shown.forEachIndexed { index, drop ->
                    if (index > 0) HorizontalDivider(color = CdColors.Gray100)
                    val sender = crew?.cars?.firstOrNull { it.id == drop.fromCarId }
                        ?: Car(drop.fromCarId, vm.carName(drop.fromCarId), CarColor.GRAY)
                    InboxRow(
                        drop = drop,
                        sender = sender,
                        status = drop.status(vm.myCarId, isSkipped),
                        timeText = agoText(now - drop.createdAtMillis),
                        onOpen = { onOpenDrop(drop) },
                    )
                }
            }
            Text("놓친 결정 드랍은 시간이 지나면 1순위로 자동 처리돼요", fontSize = 12.sp, color = CdColors.Gray600)
        }
    }
}

/** The white-grey panel on the right, 560 wide, rounded on the left (design board 07). */
@Composable
private fun BoxScope.Sheet(slide: Float, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(560.dp)
            .fillMaxHeight()
            .graphicsLayer { translationX = slide * size.width }
            .shadow(30.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            .background(CdColors.Gray100, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            // Taps inside the sheet must not fall through to the dim layer (which closes it).
            .noRippleClick {}
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun InboxRow(drop: Drop, sender: Car, status: DropStatus, timeText: String, onOpen: () -> Unit) {
    val decision = drop.kind == DropKind.DECISION
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = decision, onClick = onOpen)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CarBadge(sender, size = 30)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (decision) "결정 드랍" else "소셜 드랍",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (decision) CdColors.BlueDark else CdColors.VioletDark,
                )
                Text("${sender.name} · $timeText", fontSize = 12.sp, color = CdColors.Gray600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(drop.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when (status) {
                DropStatus.DECIDED -> drop.decidedCandidate?.let { "결정: ${it.place.name}" } ?: "결정됐어요"
                DropStatus.VOTING -> "투표 중 · ${drop.votes.size}명 응답"
                else -> drop.message
            }
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 13.sp, color = CdColors.Gray600, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (status) {
            DropStatus.NEEDS_ANSWER -> CdChip("응답하기", onClick = onOpen, style = ChipStyle.Primary)
            DropStatus.VOTING -> CdChip("보기", onClick = onOpen, style = ChipStyle.Gray)
            DropStatus.DECIDED -> CdChip("결과 보기", onClick = onOpen, style = ChipStyle.Blue)
            DropStatus.SOCIAL -> Unit
        }
    }
}

/** The four tabs at the top of the sheet. */
private enum class InboxTab(val label: String) {
    ALL("전체"),
    NEEDS_ANSWER("응답 필요"),
    SOCIAL("소셜"),
    DECIDED("지난 결정");

    fun matches(drop: Drop, myCarId: String, isSkipped: (dropId: String) -> Boolean): Boolean = when (this) {
        ALL -> true
        NEEDS_ANSWER -> drop.status(myCarId, isSkipped) == DropStatus.NEEDS_ANSWER
        SOCIAL -> drop.kind == DropKind.SOCIAL
        DECIDED -> drop.status(myCarId, isSkipped) == DropStatus.DECIDED
    }
}

/** What a drop means for this car right now. Internal so InboxStatusTest can check it. */
internal enum class DropStatus { SOCIAL, NEEDS_ANSWER, VOTING, DECIDED }

/**
 * [isSkipped]: stops this car turned down. Board 06's 가던 길 갈래요 records no vote, only a skip, so a declined
 * fuel invite must not wait for an answer here (홈's badge already leaves it out: `needsMyAnswer`).
 */
internal fun Drop.status(myCarId: String, isSkipped: (dropId: String) -> Boolean = { false }): DropStatus = when {
    kind == DropKind.SOCIAL -> DropStatus.SOCIAL
    isDecided -> DropStatus.DECIDED
    fromCarId != myCarId && myCarId !in votes && !isSkipped(id) -> DropStatus.NEEDS_ANSWER
    else -> DropStatus.VOTING
}

/** "방금", "3분 전", "1시간 전". */
internal fun agoText(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        else -> "${minutes / 60}시간 전"
    }
}

/** A tap without the grey ripple (for the dim layer and the sheet background). */
@Composable
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
