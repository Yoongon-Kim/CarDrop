package com.cardrop.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.cardrop.core.model.Drop
import com.cardrop.core.text.particle
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.voice.FakeVoiceService
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.delay
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

/**
 * Step 1, laid out like the design: this car with its fuel, then the AI's question with the two answers.
 * Either answer lets the AI pick stations; nothing is sent or routed yet.
 */
@Composable
private fun FuelQuestion(vm: DriveViewModel, onDone: () -> Unit, voice: VoiceService?) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val ready = crew != null && !action.busy
    val share = { vm.planFuelStop(shared = true) }
    val alone = { vm.planFuelStop(shared = false) }
    val myCar = crew?.cars?.firstOrNull { it.id == vm.myCarId }
    val fuel = snapshot.fuelPercent
    val listener = voice?.takeUnless { it is FakeVoiceService }
    ScreenColumn {
        FuelHeader("연료가 얼마 안 남았어요") {
            // Not in the design: the way out without answering.
            CdChip("나중에 확인", onDone, enabled = !action.busy)
        }
        // This car and its fuel.
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CdColors.White).padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            myCar?.let { CarBadge(it, size = 110) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("연료", fontSize = 15.sp, color = CdColors.Gray600)
                    Text(
                        "${fuel ?: "—"}% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "—"}km",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.OrangeDark,
                    )
                }
                Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(CdColors.Gray100)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((fuel ?: 0).coerceIn(0, 100) / 100f).background(CdColors.Orange))
                }
                val leftKm = positions[vm.myCarId]?.kmToDestination?.roundToInt()
                Text(
                    (leftKm?.let { "남은 경로 ${it}km · " } ?: "") + "Vehicle SDK 연료 데이터로 판단했어요",
                    fontSize = 14.sp, color = CdColors.Gray600,
                )
            }
        }
        // The AI's question and the two answers.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(CdColors.White).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "AI",
                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.White,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).background(CdColors.Blue)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                Text(
                    "“우리 지금 주유소 가야 할 것 같아요.\n다른 차들에게도 알릴까요?”",
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.BlueDark, lineHeight = 29.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                        .background(CdColors.BlueLight)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("응, 알려줘", share, Modifier.weight(1f).height(78.dp), ready)
                PrimaryButton(
                    "아니, 혼자 갈게", alone, Modifier.weight(1f).height(78.dp), ready,
                    containerColor = CdColors.Gray100, contentColor = CdColors.Ink2,
                )
            }
            DriveActionFeedback(action)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (listener != null) "“응”, “알려줘”라고 말해도 돼요 · 어느 쪽이든 AI가 주유소를 골라드려요"
                    else "어느 쪽이든 AI가 주유소를 골라드려요",
                    fontSize = 13.sp, color = CdColors.Gray600, modifier = Modifier.weight(1f),
                )
                if (listener != null) {
                    VoiceAnswerChip(listener, ready, setOf(DriveVoiceCommand.SHARE_FUEL, DriveVoiceCommand.ALONE)) {
                        if (it == DriveVoiceCommand.SHARE_FUEL) share() else alone()
                    }
                }
            }
        }
    }
}

/**
 * Board 05's header, both steps (target 325): an orange 14/800 eyebrow ("내 차" where the design names its demo car,
 * "민지 차 화면") and a 32/800 title, a little tighter.
 */
@Composable
private fun FuelHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    ScreenHeader(
        eyebrow = "연료 경고 · 내 차",
        title = title,
        eyebrowColor = CdColors.OrangeDark,
        eyebrowWeight = FontWeight.ExtraBold,
        titleSize = 32.sp,
        titleLetterSpacing = TightLetterSpacing,
        trailing = trailing,
    )
}

/** Step 2: the stations the AI picked. The first is its top pick; the driver may choose another. */
@Composable
private fun FuelStationPicker(vm: DriveViewModel, plan: FuelPlan, onDone: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    var chosen by remember(plan) { mutableIntStateOf(0) }
    ScreenColumn {
        FuelHeader("AI가 주유소 ${plan.stations.size}곳을 골랐어요")
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

/** Board 06: another car's fuel invitation, shown as a banner on the drive screen (board 04) like the design. */
class FuelInvite(val dropId: String, val carName: (String) -> String, val voice: VoiceService?, val onDone: () -> Unit)

/**
 * The board 06 banner. "같이 갈래요" votes for the station and puts it into this car's navigator right away;
 * "가던 길 갈래요" keeps the route. The banner then says what happened and closes after a few seconds.
 */
@Composable
internal fun FuelInviteBanner(vm: DriveViewModel, invite: FuelInvite, drop: Drop) {
    val action by vm.action.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    var passed by remember(drop.id) { mutableStateOf(vm.skipped(drop.id)) }
    val joined = vm.myCarId in drop.votes
    val answered = joined || passed
    LaunchedEffect(drop.id) { vm.clearError() }
    LaunchedEffect(drop.id, answered) {
        if (answered) {
            delay(CLOSE_AFTER_MILLIS)
            invite.onDone()
        }
    }
    val name = invite.carName(drop.fromCarId)
    val station = drop.candidates.firstOrNull()?.place
    val destination = navRoute.destination?.name ?: crew?.destination?.name ?: "목적지"
    val voice = invite.voice?.takeUnless { it is FakeVoiceService }
    val ready = !action.busy && !answered && !drop.isDecided && station != null
    val join = { vm.joinFuelStop(drop) }
    val pass = {
        vm.declineFuelStop(drop.id)
        passed = true
    }
    val (title, sub) = when {
        joined -> "같이 ${station?.name ?: "주유소"}에 들러요" to "내 내비에도 경유지로 넣었어요 · ${name}에 응답이 전달됐어요"
        passed -> "우리는 가던 길로 가요" to "경로는 그대로예요 · ${name.particle("은", "는")} ${destination}에서 합류해요"
        drop.isDecided -> "이미 결정된 알림이에요" to drop.decidedCandidate?.place?.name.orEmpty()
        else -> "${name}에서 주유소 간대요, 같이 갈래요?" to
            ("아니면 그냥 가던 길 가도 돼요" + if (voice != null) " · “같이 가자”라고 말해도 돼요" else "")
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CdColors.OrangeLight)
            .border(2.dp, CdColors.OrangeLine, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(CdColors.Orange), contentAlignment = Alignment.Center) {
            Icon(CdIcons.Fuel, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // The DEMO panel's message already starts with the car's name.
            Text(
                if (drop.message.startsWith(name)) drop.message else "$name · ${drop.message}",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.OrangeDark,
            )
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = CdColors.Gray600)
            action.error?.let { ErrorText(it) }
        }
        if (ready) {
            if (voice != null) {
                VoiceAnswerChip(voice, ready, setOf(DriveVoiceCommand.JOIN, DriveVoiceCommand.PASS)) {
                    if (it == DriveVoiceCommand.JOIN) join() else pass()
                }
            }
            PrimaryButton("가던 길 갈래요", pass, enabled = ready, containerColor = CdColors.White, contentColor = CdColors.Ink2)
            PrimaryButton("같이 갈래요", join, enabled = ready, containerColor = CdColors.Orange)
        }
    }
}

private const val CLOSE_AFTER_MILLIS = 5_000L
