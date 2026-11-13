package com.cardrop.core.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.BuildConfig
import com.cardrop.core.AppContainer
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Gear
import com.cardrop.core.model.Place
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.DropDraft
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.crew.FirebaseCrewRepository
import com.cardrop.vehicle.FakeVehicleRepository
import kotlin.math.roundToInt

/**
 * Owner: lead. A small "DEMO" button (bottom right) that stages the 시연 moments on one emulator:
 * low fuel, P/D, and drops arriving from bot cars. Those only act on the fake implementations.
 * It also switches this device between the bot crew and the real crew ("크루", [DemoCrew]), and with the
 * real crew it picks which car this device drives ("내 차", [DemoCar]).
 */
@Composable
fun DemoPanel(container: AppContainer) {
    var open by remember { mutableStateOf(false) }
    val vehicle = container.vehicle as? FakeVehicleRepository
    val crewRepo = container.crew as? FakeCrewRepository
    val snapshot by container.vehicle.snapshot.collectAsStateWithLifecycle()
    val crew by container.crew.crew.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        if (!open) {
            DemoButton("DEMO") { open = true }
            return@Box
        }
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CdColors.NavBanner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("시연 컨트롤", color = CdColors.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            CrewSwitch(realCrew = container.useRealCrew, myCar = container.myCar)
            if (container.useRealCrew) MyCarPicker(current = container.myCar)
            if (vehicle != null) {
                Text("연료 ${snapshot.fuelPercent ?: 0}%", color = CdColors.White, fontSize = 14.sp)
                Slider(
                    value = (snapshot.fuelPercent ?: 0).toFloat(),
                    onValueChange = { vehicle.setFuelPercent(it.toInt()) },
                    valueRange = 0f..100f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoButton(if (snapshot.gear == Gear.PARK) "P ✓" else "P") { vehicle.setGear(Gear.PARK) }
                    DemoButton(if (snapshot.gear == Gear.DRIVE) "D ✓" else "D") { vehicle.setGear(Gear.DRIVE) }
                }
            } else {
                Text("실차 데이터 사용 중 (useRealVehicle=true)", color = CdColors.Gray400, fontSize = 13.sp)
            }
            if (crewRepo != null && crew != null) {
                DemoButton("민지 차 연료 경고 받기") {
                    crewRepo.injectDrop(DemoData.minji, minjiFuelInvite(container))
                }
                DemoButton("삼촌 차 휴게소 투표 받기") {
                    crewRepo.injectDrop(
                        DemoData.uncle,
                        DropDraft(
                            kind = DropKind.DECISION,
                            topic = DropTopic.REST,
                            title = "어느 휴게소에서 쉴까요?",
                            message = "삼촌 차 · 2시간 연속 운전",
                            candidates = demoCandidates(container),
                        ),
                    )
                }
                DemoButton("투표 바로 마감 (시간 다 됨)") { crewRepo.closeOpenVotesNow() }
                DemoButton("이모 차 한마디 받기") {
                    crewRepo.injectDrop(
                        DemoData.aunt,
                        DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = "뒤에 사고 났어요, 조심하세요"),
                    )
                }
            } else if (crewRepo != null) {
                Text("크루를 먼저 만들어 주세요", color = CdColors.Gray400, fontSize = 13.sp)
            }
            DemoButton("닫기") { open = false }
        }
    }
}

/** The next 3 rest stops every car still has ahead (falls back to the last ones near the end). */
/**
 * 민지 차's low-fuel invite (board 06). The message names the first stop offered and 민지 차's distance to it,
 * e.g. "민지 차 · 주행 가능 85km · 횡성휴게소 31km", so the banner matches the station the crew is asked about.
 */
private fun minjiFuelInvite(container: AppContainer): DropDraft {
    val stations = demoCandidates(container)
    val stop = stations.firstOrNull()?.place
    val km = stop?.let { minjiKmTo(container, it) }
    val where = stop?.let { " · ${it.name}" + km?.let { d -> " ${d}km" }.orEmpty() }.orEmpty()
    return DropDraft(
        kind = DropKind.DECISION,
        topic = DropTopic.FUEL,
        title = "민지 차 연료가 부족해요 · 같이 갈래요?",
        message = "민지 차 · 주행 가능 85km$where",
        candidates = stations,
    )
}

/** 민지 차's distance to [place] along the live route (km); null without the in-app navigator's route. */
private fun minjiKmTo(container: AppContainer, place: Place): Int? {
    val state = container.inAppNavigation?.state?.value ?: return null
    val route = state.route ?: return null
    val positions = container.route.positions.value
    val minjiBehind = (positions[DemoData.minji.id]?.kmToDestination ?: 0f) -
        (positions[container.crew.myCarId]?.kmToDestination ?: 0f)
    val km = route.geometry.locate(place.lat, place.lng).km - state.progressKm + minjiBehind
    return km.roundToInt().takeIf { it > 0 }
}

private fun demoCandidates(container: AppContainer): List<Candidate> =
    container.stopsAhead(DemoData.restStops)
        .ifEmpty { DemoData.restStops.takeLast(3) }
        .take(3)
        .mapIndexed { i, place ->
            Candidate(
                id = place.id,
                place = place,
                reason = listOf("4대 모두 지나가는 길", "도착 시간 차이 5분", "넓은 주차장").getOrElse(i) { "" },
                detourMinutes = 2 + i * 3,
            )
        }

/**
 * Bot crew or real crew on this device ([DemoCrew]). The other one asks once, then restarts the app with it.
 * A build without app/google-services.json has only the bot crew.
 */
@Composable
private fun CrewSwitch(realCrew: Boolean, myCar: String) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Boolean?>(null) } // true = to the real crew
    Text("크루", color = CdColors.White, fontSize = 14.sp)
    if (!BuildConfig.HAS_FIREBASE) {
        Text("봇 크루 · 내 차는 아빠 차 (실제 크루는 app/google-services.json이 있어야 해요)", color = CdColors.Gray400, fontSize = 13.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tapping the crew in use cancels a pending switch, as in MyCarPicker.
        DemoButton(if (realCrew) "봇 크루" else "봇 크루 ✓") { pending = if (realCrew) false else null }
        DemoButton(if (realCrew) "실제 크루 ✓" else "실제 크루") { pending = if (realCrew) null else true }
    }
    if (!realCrew) Text("봇 크루에서는 내 차가 아빠 차예요", color = CdColors.Gray400, fontSize = 13.sp)
    pending?.let { toReal ->
        val text = if (toReal) {
            val then = if (myCar == DemoCar.HOST) "크루를 새로 만들면 다른 차는 다시 참여해야 해요." else "크루 코드로 참여해 주세요."
            "실제 크루로 바꾸면 앱이 다시 시작돼요. 내 차: ${carName(myCar)} · $then"
        } else {
            "봇 크루로 바꾸면 앱이 다시 시작돼요. 내 차: 아빠 차"
        }
        Text(text, color = CdColors.White, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton("바꾸기") { DemoCrew.pickAndRestart(context, toReal) }
            DemoButton("취소") { pending = null }
        }
    }
}

/** Real crew only: which car this device drives. Another car asks once, then restarts the app as it. */
@Composable
private fun MyCarPicker(current: String) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    Text("내 차", color = CdColors.White, fontSize = 14.sp)
    DemoCar.KEYS.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { car ->
                val name = carName(car)
                DemoButton(if (car == current) "$name ✓" else name) { pending = car.takeIf { it != current } }
            }
        }
    }
    pending?.let { car ->
        val then = if (car == DemoCar.HOST) "크루를 새로 만들어서, 다른 차는 다시 참여해야 해요." else "크루 코드로 다시 참여해 주세요."
        Text("${carName(car).withRo()} 바꾸면 앱이 다시 시작돼요. $then", color = CdColors.White, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoButton("바꾸기") { DemoCar.pickAndRestart(context, car) }
            DemoButton("취소") { pending = null }
        }
    }
}

private fun carName(car: String): String = FirebaseCrewRepository.carFor(car).name

@Composable
private fun DemoButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = CdColors.White,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CdColors.Blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
