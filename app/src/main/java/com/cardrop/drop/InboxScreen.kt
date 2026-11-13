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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Color
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
import com.cardrop.core.model.DropTopic
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

    // Clock for "3분 전" and an open vote's "1:48 남음"; ticks every second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
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
            // "전체 5", "응답 필요 2", "소셜 2", "지난 결정" (target 675): a count on every tab but the last.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InboxTab.entries.forEach { t ->
                    val count = mine.count { t.matches(it, vm.myCarId, isSkipped) }
                    TabPill(if (t == InboxTab.DECIDED) t.label else "${t.label} $count", selected = t == tab) { tab = t }
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
                        nowMillis = now,
                        onOpen = { onOpenDrop(drop) },
                    )
                }
            }
            Text("놓친 결정 드랍은 시간이 지나면 1순위로 자동 처리돼요", fontSize = 12.sp, color = CdColors.Gray600)
        }
    }
}

/** The white-grey panel on the right, 520 wide, rounded on the left (design board 07, target 283). */
@Composable
private fun BoxScope.Sheet(slide: Float, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(520.dp)
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
private fun InboxRow(drop: Drop, sender: Car, status: DropStatus, timeText: String, nowMillis: Long, onOpen: () -> Unit) {
    val decision = drop.kind == DropKind.DECISION
    val text = inboxRowText(drop, sender.name, status, nowMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = decision, onClick = onOpen)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CarBadge(sender, size = 40) // the design's default badge, 66 x 44 (target 289)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The tag (11/800, corners 6, padding 2 / 6) and only the time next to it (target 290).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = text.look.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(text.look.background)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(timeText, fontSize = 12.sp, color = CdColors.Gray600, maxLines = 1)
            }
            Text(text.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (text.sub.isNotBlank()) {
                Text(text.sub, fontSize = 13.sp, color = CdColors.Gray600, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** One tab: a round pill 40 high, dark with white text when chosen, white with grey text otherwise (target 285, 675). */
@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) CdColors.Ink else CdColors.White)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) CdColors.White else CdColors.Gray600, maxLines = 1)
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

/** The colours of a row's tag: red = waits for this car's answer, violet = social, grey = nothing left to do. */
internal enum class TagLook(val text: Color, val background: Color) {
    NEEDS_ANSWER(CdColors.Red, CdColors.RedLight),
    SOCIAL(CdColors.VioletDark, CdColors.VioletLight),
    DONE(CdColors.Gray600, CdColors.Gray100),
}

/**
 * What an inbox row says: the small tag ("결정 · 투표") in its colours, a bold title and a grey line.
 * Internal so InboxRowTest can check it.
 */
internal data class InboxRowText(val tag: String, val look: TagLook, val title: String, val sub: String)

/**
 * The row texts from the design (target 290, 667-673). Tags: "연료 알림" (a fuel invite), "결정 · 투표" (a vote),
 * "소셜 · 한마디", "결정 · 완료"; red while the drop waits for this car's answer, else grey (violet for social).
 * - a vote: "아빠 차: 어디서 쉬어갈까요?", and while it is open "덕평 · 여주 · 문막 · 1:48 남음";
 * - a fuel invite: its own title, and "같이 갈지 아직 안 골랐어요" while this car hasn't answered;
 * - a 한마디: "삼촌 차: “천천히 와요~”" over "일행에게 한마디";
 * - a finished decision: what was decided.
 * The sender's name goes in front of the title unless the title already starts with it (the DEMO panel's invite:
 * "민지 차 연료가 부족해요 · 같이 갈래요?").
 */
internal fun inboxRowText(drop: Drop, senderName: String, status: DropStatus, nowMillis: Long): InboxRowText {
    val title = if (drop.title.startsWith(senderName)) drop.title else "$senderName: ${drop.title}"
    val open = if (status == DropStatus.NEEDS_ANSWER) TagLook.NEEDS_ANSWER else TagLook.DONE
    return when {
        status == DropStatus.SOCIAL -> InboxRowText("소셜 · 한마디", TagLook.SOCIAL, "$senderName: “${drop.title}”", "일행에게 한마디")
        status == DropStatus.DECIDED ->
            InboxRowText("결정 · 완료", TagLook.DONE, title, drop.decidedCandidate?.let { "결정: ${it.place.name}" } ?: "결정됐어요")
        drop.topic == DropTopic.FUEL ->
            InboxRowText("연료 알림", open, title, if (status == DropStatus.NEEDS_ANSWER) "같이 갈지 아직 안 골랐어요" else drop.message)
        else -> InboxRowText("결정 · 투표", open, title, openVoteText(drop, nowMillis))
    }
}

/** "덕평 · 여주 · 문막 · 1:48 남음": the places to vote for, and the time left while there is some. */
private fun openVoteText(drop: Drop, nowMillis: Long): String {
    val secondsLeft = (drop.deadlineMillis - nowMillis) / 1000
    val timeLeft = if (secondsLeft > 0) "%d:%02d 남음".format(secondsLeft / 60, secondsLeft % 60) else null
    return (drop.candidates.map { shortPlaceName(it.place.name) } + listOfNotNull(timeLeft)).joinToString(" · ")
}

/** "덕평자연휴게소" -> "덕평", "여주휴게소" -> "여주", as the design shortens rest stops; other places stay whole. */
internal fun shortPlaceName(name: String): String = name.removeSuffix("자연휴게소").removeSuffix("휴게소").ifBlank { name }

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
