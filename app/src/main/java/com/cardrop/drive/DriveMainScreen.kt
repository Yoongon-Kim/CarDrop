package com.cardrop.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.dashedBorder
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.crew.JoinRules
import kotlinx.coroutines.delay

/**
 * Board 04 주행 메인, laid out like the design: the crew on the road, a card per car, the quick drops and the
 * big "드랍하기" button (its mic suggests a drop by voice). Owner: Member B (now the lead).
 * The quick drops are the driver's [quickDrops] in order (the live canvas's 퀵 드랍 with 편집), two to a row so the
 * names are big enough to read (Member A, 2026-11-15): a 2 x 2, the fifth drop on in rows under it, and one dashed
 * "+ 추가" tile after the last drop while one is left to pick. 편집 and 추가 open the editor ([onEditQuickDrops]).
 * With an [invite] it is board 06: another car's fuel invitation shows as a banner under the road.
 */
@Composable
fun DriveMainScreen(
    vm: DriveViewModel,
    quickDrops: List<QuickDrop>,
    onQuickDrop: (QuickDrop) -> Unit,
    onEditQuickDrops: () -> Unit,
    onOpenDropSend: () -> Unit,
    onOpenInbox: () -> Unit,
    onFuelWarning: () -> Unit,
    onSpeak: () -> Unit,
    invite: FuelInvite? = null,
    safetySummary: (@Composable () -> Unit)? = null,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val drops by vm.drops.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val fuel by vm.fuelByCar.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val warning by vm.fuelWarningPending.collectAsStateWithLifecycle()
    val waitingFor by vm.waitingForHost.collectAsStateWithLifecycle()
    val hasCrew = crew != null
    // Drops, the inbox and the fuel warning wait for the trip to start: the host's 출발 (board 01), or the end of
    // the 2 minutes for joining.
    val started = crew?.isStarted == true
    val canDrop = hasCrew && started
    LaunchedEffect(warning, crew?.code, started) {
        if (warning && crew != null && started) {
            vm.acknowledgeFuelWarning()
            onFuelWarning()
        }
    }
    val cars = crew?.cars.orEmpty()
    val inviteDrop = invite?.let { i -> drops.firstOrNull { it.id == i.dropId } }
    val inviteFrom = if (invite != null && inviteDrop != null) invite.carName(inviteDrop.fromCarId) else null
    val waiting = drops.count { needsMyAnswer(it, vm) }

    // 드랍하기 sits at the bottom of the screen, as in the design (`margin-top: auto`, target 274).
    ScreenColumn(bottom = {
        DropButton(
            enabled = canDrop,
            beforeStart = hasCrew && !started,
            joinDeadlineMillis = crew?.joinDeadlineMillis,
            missing = crew?.let { JoinRules.missingCars(it).size } ?: 0,
            onOpenDropSend = onOpenDropSend,
            onSpeak = onSpeak,
        )
    }) {
        // The design's header (target 203-206): eyebrow 14/700, 2 px gap, title 28/800 a little tighter, and the
        // pill and inbox button centred on the text.
        ScreenHeader(
            eyebrow = "${crew?.name ?: "크루 없음"} · ${cars.size}대 함께 · 음성 우선",
            title = when {
                waitingFor != null -> "${waitingFor.orEmpty().particle("이", "가")} 출발하면 같이 출발해요"
                hasCrew && !started -> "출발 준비 중" // the host, before its 출발
                inviteFrom != null -> "$inviteFrom 소식이 왔어요"
                hasCrew && positions.isNotEmpty() -> "모두 잘 오고 있어요"
                else -> "출발 준비 중"
            },
            eyebrowWeight = FontWeight.Bold,
            titleSize = 28.sp,
            titleLetterSpacing = TightLetterSpacing,
            gap = 2.dp,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ModePill(input)
                InboxButton(waiting, enabled = canDrop, onClick = onOpenInbox)
            }
        }

        CrewRoad(cars, positions, vm.myCarId, navRoute.destination?.name ?: crew?.destination?.name)
        if (invite != null && inviteDrop != null) FuelInviteBanner(vm, invite, inviteDrop)
        CrewCards(cars, positions, vm.myCarId, fuel, onMyLowFuel = if (canDrop) onFuelWarning else null)
        safetySummary?.invoke()

        // The quick drops: 4 px more above them than between the other parts (18 in all), 8 between label and tiles.
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The label with the white 편집 button at the right (canvas line 303: 34 high, corners 10, 13/700). The
            // slots are filled and edited before 출발 too; only sending waits for the trip to start.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (hasCrew && !started) "퀵 드랍 · 출발하면 쓸 수 있어요" else "퀵 드랍 · 누르면 바로 크루 전체에",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CdColors.Gray600,
                )
                CdChip(
                    "편집",
                    onClick = onEditQuickDrops,
                    style = ChipStyle.White,
                    height = 34.dp,
                    radius = 10.dp,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                )
            }
            // Two tiles to a row, each half the width; null = the one "+ 추가" tile. A row with one tile keeps the
            // other half empty, so every tile has the same size.
            QuickDropRules.gridRows(quickDrops).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { drop ->
                        if (drop != null) {
                            QuickTile(drop, enabled = canDrop, modifier = Modifier.weight(1f)) { onQuickDrop(drop) }
                        } else {
                            EmptyQuickSlot(Modifier.weight(1f), onClick = onEditQuickDrops)
                        }
                    }
                    repeat(QuickDropRules.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A vote or invite from another car that this car hasn't answered yet (the inbox badge's count). */
private fun needsMyAnswer(drop: Drop, vm: DriveViewModel): Boolean =
    drop.kind == DropKind.DECISION && !drop.isDecided && drop.fromCarId != vm.myCarId &&
        vm.myCarId !in drop.votes && !vm.skipped(drop.id)

/** The design's green "● 음성 모드" pill while driving; parked or not known yet keep their plain labels. */
@Composable
private fun ModePill(input: DrivingInput) {
    if (input != DrivingInput.MOVING) {
        Pill(input.label)
        return
    }
    Row(
        modifier = Modifier.height(44.dp).clip(RoundedCornerShape(999.dp)).background(CdColors.GreenLight).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(CdColors.Green))
        Text("음성 모드", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Green)
    }
}

/** The inbox button with a red count of drops waiting for this car's answer. Faded while it can't open (before 출발). */
@Composable
private fun InboxButton(waiting: Int, enabled: Boolean, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .size(56.dp)
                .alpha(if (enabled) 1f else 0.4f) // like the greyed-out quick drops
                .clip(RoundedCornerShape(18.dp))
                .background(CdColors.White)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CdIcons.Inbox, contentDescription = "드랍 편지함, 답할 드랍 ${waiting}개", tint = CdColors.Ink2, modifier = Modifier.size(26.dp))
        }
        if (waiting > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(CdColors.Alert)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$waiting", color = CdColors.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * A slot with the driver's quick drop: its icon in its colour and its name, on a white tile with corners 20
 * (canvas `.cd-quick`). Two to a row, so it is bigger than the canvas's 72: 88 high, a 46 px icon and 19 sp text ([QuickLabelSize]). Like 드랍하기, it fades and can't be tapped until the trip starts (or without a crew).
 */
@Composable
private fun QuickTile(drop: QuickDrop, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(QuickTileHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(CdColors.White)
            .border(2.dp, CdColors.White, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        QuickDropIcon(drop, size = 46.dp, iconSize = 24.dp)
        Text(
            drop.label,
            fontSize = QuickLabelSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = TightLetterSpacing,
            color = CdColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Board 04's quick-drop tiles, two to a row (the canvas's four to a row are 72 high). */
private val QuickTileHeight = 88.dp

/**
 * Their names: bigger than the canvas's 16 so they can be read from the seat, but Bold (700) with the headers' tight
 * spacing, so they sit with the rest of the screen (22 / 800 stood out next to the 28 / 800 title).
 */
private val QuickLabelSize = 19.sp

/**
 * An empty slot (canvas `.cd-quick.is-empty`): no fill, a 2 px dashed grey edge, a grey circle with "+" and
 * "추가". It opens the editor at any time, before 출발 and without a crew too.
 */
@Composable
private fun EmptyQuickSlot(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(QuickTileHeight)
            .clip(RoundedCornerShape(20.dp))
            .dashedBorder(CdColors.Gray400, radius = 20.dp, width = 2.dp, dash = 6.dp, gap = 4.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(CdColors.Gray200),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CdIcons.PlusThin, contentDescription = null, tint = CdColors.Gray600, modifier = Modifier.size(24.dp))
        }
        Text("추가", fontSize = QuickLabelSize, fontWeight = FontWeight.Bold, letterSpacing = TightLetterSpacing, color = CdColors.Gray600)
    }
}

/**
 * The design's big blue "드랍하기" button. Its mic circle is a separate button that suggests a drop by voice.
 * [beforeStart]: the crew hasn't set off yet, so it is greyed out (the greyed-out buttons' colours, so the text
 * stays readable) and its second line says why, with the time left until the trip starts by itself ([missing] =
 * the cars not joined yet).
 */
@Composable
private fun DropButton(
    enabled: Boolean,
    beforeStart: Boolean,
    joinDeadlineMillis: Long?,
    missing: Int,
    onOpenDropSend: () -> Unit,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) CdColors.Blue else CdColors.Gray200)
            .clickable(enabled = enabled, onClick = onOpenDropSend)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (enabled) CdColors.White.copy(alpha = 0.22f) else CdColors.Gray300)
                .clickable(enabled = enabled, onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CdIcons.Mic,
                contentDescription = "말로 제안하기",
                tint = if (enabled) CdColors.White else CdColors.Gray450,
                modifier = Modifier.size(26.dp),
            )
        }
        Column {
            Text("드랍하기", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (enabled) CdColors.White else CdColors.Gray450)
            if (beforeStart) {
                Text(beforeStartNote(joinDeadlineMillis, missing), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdColors.Gray600)
            } else {
                Text(
                    "말로 제안해요 · 스티어링 음성 버튼",
                    fontSize = 14.sp,
                    color = if (enabled) CdColors.White.copy(alpha = 0.9f) else CdColors.Gray600,
                )
            }
        }
    }
}

/**
 * 드랍하기's second line before the trip starts: "출발하면 드랍을 보낼 수 있어요 · 1:41 뒤 자동 출발 · 안 온 차는 빼고
 * 가요" (the 2 minutes for joining, counted down every second), or "… · 모두 모였어요 · 곧 출발해요" with every car in.
 * Without a deadline, the first part only.
 */
@Composable
private fun beforeStartNote(joinDeadlineMillis: Long?, missing: Int): String {
    val now by produceState(System.currentTimeMillis(), joinDeadlineMillis) {
        while (joinDeadlineMillis != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val countdown = joinDeadlineMillis?.let { JoinRules.autoStartText(it - now, missing) }
    return listOfNotNull("출발하면 드랍을 보낼 수 있어요", countdown).joinToString(" · ")
}
