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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.AppContainer
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Gear
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.DropDraft
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.vehicle.FakeVehicleRepository

/**
 * Owner: lead. A small "DEMO" button (bottom right) that stages the 시연 moments on one emulator:
 * low fuel, P/D, and drops arriving from bot cars. It only acts on the fake implementations.
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
                    crewRepo.injectDrop(DemoData.minji, FakeCrewRepository.demoFuelDraft(demoCandidates(container)))
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
