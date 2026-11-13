package com.cardrop.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.voice.VoiceService

/** Board 05: propose once; only the driver's explicit answer sends a crew drop. */
@Composable
fun FuelMeScreen(vm: DriveViewModel, onDone: () -> Unit, voice: VoiceService? = null) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.acknowledgeFuelWarning(); vm.clearError() }
    val ready = crew != null && !action.busy
    val share = { vm.alertCrewAboutFuel { onDone() } }
    val alone = { vm.goToStationAlone(onDone) }
    ScreenColumn {
        ScreenHeader("연료 경고 · 내 차", "연료 ${snapshot.fuelPercent ?: "—"}% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "—"}km")
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CdColors.OrangeLight).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("연료가 부족해요", CdColors.Orange, CdColors.White)
                Text("우리 지금 주유소에 들러야 할 것 같아요.\n다른 차들에게도 알릴까요?", style = MaterialTheme.typography.headlineLarge)
                Text("전방 주유소와 진입 경로를 확인한 뒤 추천해요. 남은 주행 거리에서 5km를 여유로 남겨요.")
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
                drop.candidates.firstOrNull()?.let { InfoRow(it.place.name, "${it.reason} · 최종 장소는 투표 후 확정돼요") }
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
