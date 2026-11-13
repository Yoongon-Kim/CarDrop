package com.cardrop.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.voice.VoiceService
import kotlin.math.roundToInt

/**
 * Board 05, in two steps like the design: the car asks once ("응, 알려줘" / "아니, 혼자 갈게"), then the AI shows
 * up to 3 stations, and only the station the driver picks is sent to the crew or put into the navigator.
 */
@Composable
fun FuelMeScreen(vm: DriveViewModel, onDone: () -> Unit, voice: VoiceService? = null) {
    val plan by vm.fuelPlan.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.acknowledgeFuelWarning(); vm.cancelFuelPlan() }
    val stations = plan
    if (stations == null) FuelQuestion(vm, onDone, voice) else FuelStationPicker(vm, stations, onDone)
}

/** Step 1: the car's question. Either answer lets the AI pick stations; nothing is sent or routed yet. */
@Composable
private fun FuelQuestion(vm: DriveViewModel, onDone: () -> Unit, voice: VoiceService?) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val ready = crew != null && !action.busy
    val share = { vm.planFuelStop(shared = true) }
    val alone = { vm.planFuelStop(shared = false) }
    ScreenColumn {
        ScreenHeader("연료 경고 · 내 차", "연료 ${snapshot.fuelPercent ?: "—"}% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "—"}km")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CdColors.OrangeLight).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("연료가 부족해요", CdColors.Orange, CdColors.White)
                Text("우리 지금 주유소에 들러야 할 것 같아요.\n다른 차들에게도 알릴까요?", style = MaterialTheme.typography.headlineLarge)
                Text("어느 쪽이든 AI가 갈 수 있는 주유소를 골라드려요. 남은 주행 거리에서 5km를 여유로 남겨요.")
            }
        }
        Pill(input.label)
        DriveActionFeedback(action)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("응, 알려줘", share, Modifier.weight(1f), ready)
            SecondaryButton("아니, 혼자 갈게", alone, Modifier.weight(1f), ready)
        }
        DriveVoiceReply(voice, "“알려줘” 또는 “혼자 갈게”라고 말해 주세요", ready,
            setOf(DriveVoiceCommand.SHARE_FUEL, DriveVoiceCommand.ALONE)) {
            when (it) {
                DriveVoiceCommand.SHARE_FUEL -> share()
                DriveVoiceCommand.ALONE -> alone()
                else -> Unit
            }
        }
        SecondaryButton("나중에 확인", onDone, enabled = !action.busy)
    }
}

/** Step 2: the stations the AI picked. The first is its top pick; the driver may choose another. */
@Composable
private fun FuelStationPicker(vm: DriveViewModel, plan: FuelPlan, onDone: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    var chosen by remember(plan) { mutableIntStateOf(0) }
    ScreenColumn {
        ScreenHeader("연료 경고 · 내 차", "AI가 주유소 ${plan.stations.size}곳을 골랐어요")
        Text("가는 길에 있고, 남은 연료로 갈 수 있는 곳만 골랐어요", style = MaterialTheme.typography.bodyLarge, color = CdColors.Gray600)
        plan.stations.forEachIndexed { i, station ->
            StationRow(i + 1, station, selected = i == chosen, rangeKm = snapshot.rangeKm, enabled = !action.busy) { chosen = i }
        }
        DriveActionFeedback(action)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                if (plan.shared) "여기로 정하고 일행에게 알리기" else "여기로 혼자 다녀오기",
                { vm.confirmFuelStop(chosen, onDone) },
                Modifier.weight(2f),
                !action.busy,
            )
            SecondaryButton("취소", { vm.cancelFuelPlan() }, Modifier.weight(1f), !action.busy)
        }
    }
}

/** One station, laid out like the design: number, name, distance and reason on the left; minutes on the right. */
@Composable
private fun StationRow(number: Int, station: FuelStation, selected: Boolean, rangeKm: Float?, enabled: Boolean, onClick: () -> Unit) {
    val place = station.candidate.place
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(if (selected) CdColors.YellowLight else CdColors.White)
            .border(2.dp, if (selected) CdColors.Amber else CdColors.White, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (selected) CdColors.Amber else CdColors.Gray100),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) CdColors.White else CdColors.Ink2)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
                if (number == 1) SmallTag("AI 추천")
            }
            val details = listOfNotNull(station.drivingKm?.let { "${it.roundToInt()}km" }, place.detail.takeIf { it.isNotBlank() })
            if (details.isNotEmpty()) Text(details.joinToString(" · "), fontSize = 14.sp, color = CdColors.Gray600)
            if (station.candidate.reason.isNotBlank()) {
                Text(station.candidate.reason, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdColors.BlueDark)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            station.minutes?.let { Text("${it}분", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink) }
            rangeKm?.let { Text("주행 가능 ${it.toInt()}km", fontSize = 13.sp, color = CdColors.Gray600) }
        }
    }
}

/** The design's small "AI 추천" badge. */
@Composable
private fun SmallTag(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = CdColors.BlueDark,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CdColors.BlueLight)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Board 06: fuel invitation appears above the driving overview. No provisional reroute. */
@Composable
fun FuelThemScreen(vm: DriveViewModel, dropId: String, carName: (String) -> String, onDone: () -> Unit, voice: VoiceService? = null) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    LaunchedEffect(dropId) { vm.clearError() }
    val drop = drops.firstOrNull { it.id == dropId }
    ScreenColumn {
        if (drop == null) {
            ScreenHeader("연료 알림", "알림을 찾을 수 없어요")
            SecondaryButton("주행 화면으로", onDone)
            return@ScreenColumn
        }
        ScreenHeader("${crew?.name ?: "크루"} · 함께 주행 중", navRoute.destination?.name ?: crew?.destination?.name ?: "주행 메인")
        val ready = !action.busy && !drop.isDecided && !vm.skipped(drop.id)
        val join = { vm.joinFuelStop(drop, onDone) }
        val pass = { vm.declineFuelStop(drop.id); onDone() }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CdColors.OrangeLight).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("${carName(drop.fromCarId)} · 연료 알림", CdColors.Orange, CdColors.White)
                Text(drop.title, style = MaterialTheme.typography.titleLarge)
                Text(drop.message)
                drop.candidates.firstOrNull()?.let {
                    // A drop from board 05 names one station; only the DEMO panel's drop still has several to vote on.
                    val voteNote = if (drop.candidates.size > 1) " · 최종 장소는 투표 후 확정돼요" else ""
                    InfoRow(it.place.name, it.reason + voteNote)
                }
                if (drop.isDecided) StatusText("이미 결정된 알림이에요")
                if (vm.skipped(drop.id)) StatusText("우리 차는 가던 길로 가요")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("가던 길 갈래요", pass, Modifier.weight(1f), ready)
                    PrimaryButton("같이 갈래요", join, Modifier.weight(1f), ready && drop.candidates.isNotEmpty())
                }
            }
        }
        DriveActionFeedback(action)
        Pill(input.label)
        DriveVoiceReply(voice, "“같이 갈래요” 또는 “가던 길 갈래요”라고 말해 주세요", ready,
            setOf(DriveVoiceCommand.JOIN, DriveVoiceCommand.PASS)) {
            if (it == DriveVoiceCommand.JOIN) join() else pass()
        }
        CrewDriveOverview(crew?.cars.orEmpty(), positions, vm.myCarId)
        if (!ready) SecondaryButton("주행 화면으로", onDone, enabled = !action.busy)
    }
}
