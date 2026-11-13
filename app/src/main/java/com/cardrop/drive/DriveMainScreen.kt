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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors

/**
 * Board 04 주행 메인, laid out like the design: the crew on the road, a card per car, four quick drops and the
 * big "드랍하기" button (its mic suggests a drop by voice). Owner: Member B (now the lead).
 * With an [invite] it is board 06: another car's fuel invitation shows as a banner under the road.
 */
@Composable
fun DriveMainScreen(
    vm: DriveViewModel,
    onQuickDrop: (DropTopic) -> Unit,
    onOpenDropSend: () -> Unit,
    onOpenInbox: () -> Unit,
    onFuelWarning: () -> Unit,
    onSpeak: () -> Unit,
    onShout: () -> Unit = {},
    invite: FuelInvite? = null,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val drops by vm.drops.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val warning by vm.fuelWarningPending.collectAsStateWithLifecycle()
    val waitingFor by vm.waitingForHost.collectAsStateWithLifecycle()
    LaunchedEffect(warning, crew?.code) {
        if (warning && crew != null) {
            vm.acknowledgeFuelWarning()
            onFuelWarning()
        }
    }
    val hasCrew = crew != null
    val cars = crew?.cars.orEmpty()
    val inviteDrop = invite?.let { i -> drops.firstOrNull { it.id == i.dropId } }
    val inviteFrom = if (invite != null && inviteDrop != null) invite.carName(inviteDrop.fromCarId) else null
    val waiting = drops.count { needsMyAnswer(it, vm) }

    ScreenColumn {
        ScreenHeader(
            eyebrow = "${crew?.name ?: "크루 없음"} · ${cars.size}대 함께 · 음성 우선",
            title = when {
                waitingFor != null -> "${waitingFor.orEmpty().particle("이", "가")} 출발하면 같이 출발해요"
                inviteFrom != null -> "$inviteFrom 소식이 왔어요"
                hasCrew && positions.isNotEmpty() -> "모두 잘 오고 있어요"
                else -> "출발 준비 중"
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ModePill(input)
                InboxButton(waiting, enabled = hasCrew, onClick = onOpenInbox)
            }
        }

        CrewRoad(cars, positions, vm.myCarId, navRoute.destination?.name ?: crew?.destination?.name)
        if (invite != null && inviteDrop != null) FuelInviteBanner(vm, invite, inviteDrop)
        CrewCards(
            cars, positions, vm.myCarId, snapshot.fuelPercent, snapshot.rangeKm,
            onMyLowFuel = if (hasCrew) onFuelWarning else null,
        )

        Text("퀵 드랍 · 누르면 바로 보낼 곳을 골라요", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Gray600)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile("휴게소", CdIcons.Rest, CdColors.BlueLight, CdColors.BlueDark, hasCrew, Modifier.weight(1f)) { onQuickDrop(DropTopic.REST) }
            QuickTile("식사", CdIcons.Food, CdColors.OrangeLight, CdColors.OrangeDeep, hasCrew, Modifier.weight(1f)) { onQuickDrop(DropTopic.FOOD) }
            QuickTile("주유", CdIcons.Fuel, CdColors.YellowLight, CdColors.YellowDeep, hasCrew, Modifier.weight(1f)) { onQuickDrop(DropTopic.FUEL) }
            QuickTile("한마디", CdIcons.Shout, CdColors.VioletLight, CdColors.VioletDark, hasCrew, Modifier.weight(1f), onShout)
        }

        DropButton(enabled = hasCrew, onOpenDropSend = onOpenDropSend, onSpeak = onSpeak)
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

/** The inbox button with a red count of drops waiting for this car's answer. */
@Composable
private fun InboxButton(waiting: Int, enabled: Boolean, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .size(56.dp)
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

/** One of the four quick drops: an icon in its colour and a label, on a white tile. */
@Composable
private fun QuickTile(
    label: String,
    icon: ImageVector,
    iconBackground: Color,
    iconColor: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(72.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(CdColors.White)
            .border(2.dp, CdColors.White, shape)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
    }
}

/** The design's big blue "드랍하기" button. Its mic circle is a separate button that suggests a drop by voice. */
@Composable
private fun DropButton(enabled: Boolean, onOpenDropSend: () -> Unit, onSpeak: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) CdColors.Blue else CdColors.Gray300)
            .clickable(enabled = enabled, onClick = onOpenDropSend)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CdColors.White.copy(alpha = 0.22f))
                .clickable(enabled = enabled, onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CdIcons.Mic, contentDescription = "말로 제안하기", tint = CdColors.White, modifier = Modifier.size(26.dp))
        }
        Column {
            Text("드랍하기", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.White)
            Text("마이크를 누르면 말로 제안해요", fontSize = 14.sp, color = CdColors.White.copy(alpha = 0.9f))
        }
    }
}
