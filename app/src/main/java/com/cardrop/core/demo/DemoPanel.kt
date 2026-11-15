package com.cardrop.core.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.cardrop.drive.GeoPoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.BuildConfig
import com.cardrop.core.AppContainer
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Car
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Gear
import com.cardrop.core.model.Place
import com.cardrop.core.text.particle
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.DropDraft
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.crew.FirebaseCrewRepository
import com.cardrop.drop.GameInviteRules
import com.cardrop.drop.ProposalRules
import com.cardrop.drop.SongRules
import com.cardrop.vehicle.FakeVehicleRepository
import com.cardrop.safety.SafetyEventType
import kotlin.math.roundToInt

/**
 * Owner: lead. A small "DEMO" button (in the rail: [DemoRailButton]) that stages the 시연 moments on one emulator:
 * low fuel, P/D, and drops arriving from bot cars. Those only act on the fake implementations.
 * "내 내비를 서울 신촌역으로" points this car's navigator at 신촌역, so board
 * 03 offers "서울 신촌역 들렀다 가기" when the car then joins a crew.
 * It also switches this device between the bot crew and the real crew ("크루", [DemoCrew]), and with the
 * real crew it picks which car this device drives ("내 차", [DemoCar]).
 */
@Composable
fun DemoPanel(container: AppContainer, open: Boolean, onClose: () -> Unit) {
    val vehicle = container.vehicle as? FakeVehicleRepository
    val crewRepo = container.demoCrew
    val snapshot by container.vehicle.snapshot.collectAsStateWithLifecycle()
    val crew by container.crew.crew.collectAsStateWithLifecycle()

    // Opens at the bottom left of the screen, next to its button in the rail. 460 wide, so the buttons sit two or
    // three to a row and the whole panel fits on the screen without scrolling.
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
        if (!open) return@Box
        Column(
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 790.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CdColors.NavBanner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The title and a ✕ that never scroll away, even when the controls below have to scroll (a long real crew).
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("시연 컨트롤", color = CdColors.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                CloseX(onClose)
            }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CrewSwitch(realCrew = container.useRealCrew, myCar = container.myCar)
                if (container.useRealCrew) MyCarPicker(current = container.myCar)
                if (vehicle != null) {
                    Text("연료 ${snapshot.fuelPercent ?: 0}%", color = CdColors.White, fontSize = 14.sp)
                    Slider(
                        value = (snapshot.fuelPercent ?: 0).toFloat(),
                        onValueChange = { vehicle.setFuelPercent(it.toInt()) },
                        valueRange = 0f..100f,
                    )
                    DemoButtons {
                        DemoButton(if (snapshot.gear == Gear.PARK) "P ✓" else "P") { vehicle.setGear(Gear.PARK) }
                        DemoButton(if (snapshot.gear == Gear.DRIVE) "D ✓" else "D") { vehicle.setGear(Gear.DRIVE) }
                    }
                } else {
                    Text("실차 데이터 사용 중 (useRealVehicle=true)", color = CdColors.Gray400, fontSize = 13.sp)
                }
                // The bots send drops only on the road, like the real cars, and only the bots that came along (a bot
                // that hadn't joined by 출발 was left out).
                val current = crew
                if (container.safety.demo && current?.isStarted == true) {
                    Text("안전운전 DEMO · 같은 감점 판정기 사용 · D에서 감점 입력, P에서 도착 / 종료", color = CdColors.Gray400, fontSize = 12.sp)
                    var gap by remember { mutableStateOf(false) }
                    DemoButtons {
                        DemoButton("급가속") { container.safety.demoTelemetry?.scenario(SafetyEventType.ACCELERATION) }
                        DemoButton("급정거") { container.safety.demoTelemetry?.scenario(SafetyEventType.BRAKING) }
                        DemoButton(if (gap) "속도 입력 재개" else "속도 입력 끊기") {
                            gap = !gap; container.safety.demoTelemetry?.paused = gap
                        }
                        DemoButton("P · 최종 도착 재현") { vehicle?.setGear(Gear.PARK); container.safety.demoArrival() }
                    }
                }
                val along = { bot: Car -> current?.cars?.any { it.id == bot.id } == true }
                if (crewRepo != null && current != null && current.isStarted) {
                    DemoButtons {
                        if (along(DemoData.minji)) {
                            DemoButton("${DemoData.minji.name} 연료 경고 받기") {
                                crewRepo.injectDrop(DemoData.minji, minjiFuelInvite(container))
                            }
                        }
                        if (along(DemoData.uncle)) {
                            DemoButton("${DemoData.uncle.name} 휴게소 투표 받기") {
                                crewRepo.injectDrop(
                                    DemoData.uncle,
                                    DropDraft(
                                        kind = DropKind.DECISION,
                                        topic = DropTopic.REST,
                                        title = "어디서 쉬어갈까요?",
                                        message = "${DemoData.uncle.name} · 2시간 연속 운전",
                                        candidates = demoCandidates(container),
                                    ),
                                )
                            }
                        }
                        // A quick drop from a bot (04's pop-up, one place): board 11 asks only 예 / 아니요 (Member A).
                        if (along(DemoData.uncle)) {
                            DemoButton("${DemoData.uncle.name} 퀵 드랍 받기 (예/아니요)") {
                                demoCandidates(container).firstOrNull()?.let { place ->
                                    crewRepo.injectDrop(
                                        DemoData.uncle,
                                        DropDraft(
                                            kind = DropKind.DECISION,
                                            topic = DropTopic.REST,
                                            title = "휴게소 들렀다 가요",
                                            message = "${DemoData.uncle.name} · 퀵 드랍",
                                            candidates = listOf(place),
                                            senderVotes = false,
                                        ),
                                    )
                                }
                            }
                        }
                        // A destination proposal from a bot (board 11's 찬성 / 반대 / 다른 목적지 제안하기), added by Member A:
                        // 속초 해수욕장, or another known destination when the crew is heading there already.
                        DemoButton("${DemoData.uncle.name} 목적지 제안 받기") {
                            val now = crew?.destination
                            val others = DemoData.destinations.filterNot { ProposalRules.isCurrent(it, now) }
                            (others.firstOrNull { it.id == "sokcho" } ?: others.firstOrNull())?.let { place ->
                                crewRepo.injectDrop(DemoData.uncle, ProposalRules.draft(place, toCarIds = emptyList()))
                            }
                        }
                        // A song drop from a bot (board 13), added by Member A.
                        if (along(DemoData.uncle)) {
                            DemoButton("${DemoData.uncle.name} 노래 드랍 받기") {
                                crewRepo.injectDrop(DemoData.uncle, SongRules.draft(SongRules.SONGS.first(), toCarIds = emptyList()))
                            }
                        }
                        // A 게임 초대 from a bot (Member A): the pop-up in P, only the inbox while driving.
                        if (along(DemoData.uncle)) {
                            DemoButton("${DemoData.uncle.name} 게임 초대 받기") {
                                crewRepo.injectDrop(DemoData.uncle, GameInviteRules.quiz("상식 퀴즈", toCarIds = emptyList()))
                            }
                        }
                        // A voice message from a bot (Member A): the last one this car recorded on board 09, sent back as
                        // if 삼촌 차 said it, so the banner's ▶ 재생 can be tried on one emulator.
                        if (along(DemoData.uncle)) {
                            DemoButton("${DemoData.uncle.name} 음성 메시지 받기 (내 마지막 녹음)") {
                                val mine = crewRepo.drops.value.lastOrNull { it.topic == DropTopic.VOICE && it.fromCarId == crewRepo.myCarId }
                                if (mine != null) {
                                    crewRepo.injectDrop(
                                        DemoData.uncle,
                                        DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.VOICE, title = mine.title, message = mine.message),
                                    )
                                }
                            }
                        }
                        DemoButton("투표 바로 마감 (시간 다 됨)") { crewRepo.closeOpenVotesNow() }
                        if (along(DemoData.aunt)) {
                            DemoButton("${DemoData.aunt.name} 한마디 받기") {
                                crewRepo.injectDrop(
                                    DemoData.aunt,
                                    DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = "뒤에 사고 났어요, 조심하세요"),
                                )
                            }
                        }
                    }
                } else if (crewRepo != null && current != null) {
                    Text("출발하면 봇 차의 드랍을 받을 수 있어요", color = CdColors.Gray400, fontSize = 13.sp)
                } else if (crewRepo != null) {
                    Text("크루를 먼저 만들어 주세요", color = CdColors.Gray400, fontSize = 13.sp)
                }
                // Board 03's "…들렀다 가기" shows only when this car's navigator was heading somewhere else before it
                // joined. The app has no way to set a route without a crew, so this button stages that moment:
                // press it, then join with the code.
                DemoButton("내 내비를 ${DemoData.seoul.name.withRo()} (03 들렀다 가기)") {
                    container.navigation.setRoute(
                        DemoData.seoul,
                        waypoint = null,
                        reason = "${DemoData.seoul.name.withRo()} 안내해요",
                    )
                }
                // The 시연 scenario (Member A, 2026-11-15): the car was heading to a pharmacy when the crew's code came.
                // Kakao finds the pharmacy nearest 신촌역 (a real place); without a key or the internet, it says so.
                val scope = rememberCoroutineScope()
                var pharmacyNote by remember { mutableStateOf<String?>(null) }
                DemoButton("내 내비를 신촌 약국으로 (03 들렀다 가기)") {
                    scope.launch {
                        val near = GeoPoint(DemoData.seoul.lat, DemoData.seoul.lng)
                        val found = runCatching { container.placeSearch?.search("신촌 약국", near) }.getOrNull()
                            ?.map { it.place }?.firstOrNull { "약국" in it.name }
                        if (found == null) {
                            pharmacyNote = "신촌 근처 약국을 찾지 못했어요 · 카카오 키와 인터넷이 있어야 해요"
                        } else {
                            pharmacyNote = found.name
                            container.navigation.setRoute(found, waypoint = null, reason = "${found.name.withRo()} 안내해요")
                        }
                    }
                }
                pharmacyNote?.let { Text(it, color = CdColors.Gray400, fontSize = 12.sp) }
            }
        }
    }
}

/** The panel's ✕ at the top right: a 36 px circle, a little lighter than the panel, with a white cross. */
@Composable
private fun CloseX(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(CdColors.White.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CdIcons.Close, contentDescription = "닫기", tint = CdColors.White, modifier = Modifier.size(20.dp))
    }
}

/** Buttons side by side, wrapping to the next line when the row is full, 8 apart both ways. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/**
 * The "DEMO" button that opens and closes [DemoPanel]. CarDropApp puts it in the rail, above 내 차: in the
 * screen's bottom-right corner, where it used to be, it covered the end of the buttons boards pin to the bottom
 * (10's 투표 보내기, 11's 응답하기, 01's 출발, …).
 */
@Composable
fun DemoRailButton(onClick: () -> Unit) {
    DemoButton("DEMO", onClick)
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
        title = "${DemoData.minji.name} 연료가 부족해요 · 같이 갈래요?",
        message = "${DemoData.minji.name} · 주행 가능 85km$where",
        candidates = stations,
    )
}

/** 민지 차's distance to [place] along the live route (km); null before the navigator has a route. */
private fun minjiKmTo(container: AppContainer, place: Place): Int? {
    val state = container.inAppNavigation.state.value
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
        Text("봇 크루 · 내 차는 ${carName(DemoCar.HOST)} (실제 크루는 app/google-services.json이 있어야 해요)", color = CdColors.Gray400, fontSize = 13.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tapping the crew in use cancels a pending switch, as in MyCarPicker.
        DemoButton(if (realCrew) "봇 크루" else "봇 크루 ✓") { pending = if (realCrew) false else null }
        DemoButton(if (realCrew) "실제 크루 ✓" else "실제 크루") { pending = if (realCrew) null else true }
    }
    if (!realCrew) Text("봇 크루에서는 내 차가 ${carName(DemoCar.HOST).particle("이에요", "예요")}", color = CdColors.Gray400, fontSize = 13.sp)
    pending?.let { toReal ->
        val text = if (toReal) {
            val then = if (myCar == DemoCar.HOST) "크루를 새로 만들면 다른 차는 다시 참여해야 해요." else "크루 코드로 참여해 주세요."
            "실제 크루로 바꾸면 앱이 다시 시작돼요. 내 차: ${carName(myCar)} · $then"
        } else {
            "봇 크루로 바꾸면 앱이 다시 시작돼요. 내 차: ${carName(DemoCar.HOST)}"
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
