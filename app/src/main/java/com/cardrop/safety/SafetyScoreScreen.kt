package com.cardrop.safety

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Crew
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Boards 16 and 17. All numbers come from the trip journal, including the demo cars. */
@Composable
fun SafetyScoreScreen(safety: SafetyCoordinator, crew: Crew?, onWeek: () -> Unit = {}, onResult: (String) -> Unit) {
    val record by safety.current.collectAsStateWithLifecycle()
    val peers by safety.peers.collectAsStateWithLifecycle()
    val history by safety.history.collectAsStateWithLifecycle()
    val speed by safety.speedKmh.collectAsStateWithLifecycle()
    val source by safety.sourceState.collectAsStateWithLifecycle()
    val parked by safety.canFinish.collectAsStateWithLifecycle()
    val closed by safety.groupClosed.collectAsStateWithLifecycle()
    val now by safety.clock.collectAsStateWithLifecycle()
    val cars = crew?.cars.orEmpty()
    val ranked = rankSafetyCars(cars, peers, now, safety.demo, live = true)
    val mine = record
    var confirmEnd by remember(mine?.id) { mutableStateOf(false) }
    LaunchedEffect(parked) { if (!parked) confirmEnd = false }

    ScreenColumn(gap = 14.dp) {
        ScreenHeader(
            eyebrow = "안전운전 대결 · ${if (safety.demo) "DEMO · 모의 주행" else "차량 속도 기록"}",
            title = when { mine == null -> "함께, 안전하게 도착해요"; mine.status == SafetyTripStatus.RECORDING -> "안전한 주행을 기록하고 있어요"; else -> "안전한 여정을 기록했어요" },
            titleSize = 28.sp,
        ) { Pill(if (closed) "크루 집계 종료" else if (mine?.status == SafetyTripStatus.RECORDING) "주행 기록 중" else "내 차 운전 기록") }

        if (mine == null) {
            SafetyEncouragementCard(safety)
            CdCard(Modifier.fillMaxWidth()) {
                Icon(CdIcons.Trophy, null, tint = CdColors.Blue, modifier = Modifier.size(40.dp))
                Text("출발하면 100점에서 시작해요", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("급가속 · 급정거만 감점해요. 크루를 만들거나 참여한 뒤 출발해 주세요.", color = CdColors.Gray600)
            }
        } else {
            SafetyRoad(ranked)
            // The right column ends where the ranking card ends (Member A, 2026-11-15): it takes that card's height, and
            // "이번 주 내 기록" fills what the AI card leaves.
            var rankHeight by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                CdCard(Modifier.weight(1.14f).onSizeChanged { rankHeight = it.height }, gap = 8.dp, contentPadding = PaddingValues(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("실시간 잠정 순위")
                        Text("${time(now)} · 매초 갱신", fontSize = 12.sp, color = CdColors.Gray500)
                    }
                    ranked.forEach { SafetyCarRow(it, now, safety.demo) }
                    Text("급가속 · 급정거 기준 · 동점은 공동 순위\n확정 순위는 도착 후 주행 1분 · 속도 측정 95% 기준을 적용해요", fontSize = 11.sp, color = CdColors.Gray500)
                }
                Column(
                    Modifier.weight(1f).then(if (rankHeight > 0) Modifier.height(with(density) { rankHeight.toDp() }) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SafetyEncouragementCard(safety)
                    WeekCard(
                        trips = weekTrips(history, safety.demo, now),
                        demo = safety.demo,
                        onOpen = onWeek,
                        modifier = Modifier.fillMaxWidth().then(if (rankHeight > 0) Modifier.weight(1f) else Modifier),
                    )
                }
            }
            CdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp), gap = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        SectionLabel("내 차 안전점수")
                        Text("${mine.score}점", fontSize = 36.sp, color = CdColors.BlueDark, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${speed?.roundToInt()?.toString() ?: "—"} km/h", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(sourceLabel(source, mine), fontSize = 13.sp, color = CdColors.Gray600)
                        if (!safety.demo && source in listOf(SafetySourceState.PERMISSION_DENIED, SafetySourceState.UNAVAILABLE)) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Text("속도 권한 / 연결 확인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdColors.BlueDark,
                                modifier = Modifier.clickable { safety.retrySpeed(context) }.padding(6.dp))
                        }
                    }
                }
                SafetyMetrics(mine)
                QualityLine(mine)
                Text("100점 − 감점 합계 ${mine.totalPenalty}점 · 점수는 주행 중 회복되지 않아요", fontSize = 12.sp, color = CdColors.Gray500)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mine.status == SafetyTripStatus.RECORDING) "최종 목적지 도착 후 정차하면 결과가 저장돼요" else "내 차 기록이 저장되었어요",
                    modifier = Modifier.weight(1f), fontSize = 13.sp, color = CdColors.Gray600)
                if (mine.status == SafetyTripStatus.RECORDING) {
                    PrimaryButton(if (confirmEnd) "중도 종료 확정" else "기록 종료", {
                        if (confirmEnd) { safety.finish(); confirmEnd = false } else confirmEnd = true
                    }, enabled = parked, height = 46.dp, radius = 14.dp, fontSize = 15.sp)
                    if (confirmEnd) Text("취소", color = CdColors.BlueDark, modifier = Modifier.clickable { confirmEnd = false }.padding(10.dp))
                } else PrimaryButton("내 차 결과 보기", { onResult(mine.id) }, height = 46.dp, radius = 14.dp, fontSize = 15.sp)
            }
            if (confirmEnd) Text("중도 종료한 기록은 저장되며 최종 순위에서 제외돼요", color = CdColors.OrangeDark, fontSize = 13.sp)
        }
        HistoryCard(history.filter { it.id != mine?.id }, parked, onResult)
    }
}

@Composable
private fun SafetyEncouragementCard(safety: SafetyCoordinator) {
    val enabled by safety.cheerEnabled.collectAsStateWithLifecycle()
    val busy by safety.cheerBusy.collectAsStateWithLifecycle()
    val cheer by safety.cheerText.collectAsStateWithLifecycle()
    CdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp), gap = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel("AI 응원 · 내 차에게만")
            Switch(checked = enabled, onCheckedChange = safety::setCheerEnabled, modifier = Modifier.height(30.dp))
        }
        Text(if (!enabled) "응원이 꺼져 있어요" else if (busy) "지금 주행에 맞는 응원을 준비해요…" else cheer,
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdColors.BlueDark,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(CdColors.BlueLight).padding(14.dp))
        Text("기록 메뉴나 운전 기록 퀵 드랍을 누를 때마다 한 번씩 들려드려요", fontSize = 12.sp, color = CdColors.Gray500)
    }
}

@Composable
fun SafetyResultScreen(safety: SafetyCoordinator, recordId: String, crew: Crew?, onBack: () -> Unit) {
    val history by safety.history.collectAsStateWithLifecycle()
    val current by safety.current.collectAsStateWithLifecycle()
    val peers by safety.peers.collectAsStateWithLifecycle()
    val parked by safety.canFinish.collectAsStateWithLifecycle()
    val closed by safety.groupClosed.collectAsStateWithLifecycle()
    val now by safety.clock.collectAsStateWithLifecycle()
    val record = current?.takeIf { it.id == recordId } ?: history.firstOrNull { it.id == recordId }
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BackButton(onBack)
            ScreenHeader(eyebrow = record?.let { "${it.destination} · ${date(it.startedAtMillis)} · ${if (it.demo) "DEMO" else "실제 주행"}" },
                title = "오늘의 안전운전 기록", titleSize = 28.sp)
        }
        if (!parked) {
            CdCard(Modifier.fillMaxWidth()) {
                SectionLabel("정차 후 자세한 기록을 볼 수 있어요")
                Text("주행 중에는 기록 화면의 요약을 확인해 주세요.", color = CdColors.Gray600)
            }
        } else if (record == null) {
            CdCard(Modifier.fillMaxWidth()) { Text("저장된 기록을 찾을 수 없어요") }
        } else {
            val activeTrip = record.tripId == current?.tripId
            val eligiblePeers = peers.filterValues { it.status == SafetyTripStatus.COMPLETED }
            val ranks = if (activeTrip) rankSafetyCars(crew?.cars.orEmpty(), eligiblePeers, now, record.demo).filter { it.rank != null } else emptyList()
            CdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        SectionLabel(when (record.status) { SafetyTripStatus.COMPLETED -> "도착 완료 · 내 차 결과"; SafetyTripStatus.STOPPED -> "중도 종료 · 최종 순위 제외"; else -> "진행 중인 기록" })
                        Text("${record.score}점", color = CdColors.BlueDark, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    val me = crew?.cars?.firstOrNull { it.id == record.carId } ?: Car(record.carId, record.carName, CarColor.BLUE, isMe = true)
                    CarBadge(me, size = 85, bob = false)
                }
                Text("${record.carName} · ${record.movingSeconds.toInt() / 60}분 주행 · ${String.format(Locale.KOREA, "%.1f", record.distanceKm)}km 기록", color = CdColors.Gray600, fontSize = 15.sp)
                SafetyMetrics(record)
                QualityLine(record)
            }
            if (activeTrip) {
                CdCard(Modifier.fillMaxWidth(), gap = 14.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("크루 ${if (closed) "최종 결과" else "도착 결과 · 집계 중"}")
                        Text("완주 · 데이터 기준 충족 차량", color = CdColors.Gray500, fontSize = 12.sp)
                    }
                    if (ranks.isEmpty()) Text("아직 최종 순위에 참여할 수 있는 완주 기록이 없어요", color = CdColors.Gray600, fontSize = 15.sp)
                    else SafetyPodium(ranks)
                    val rows = rankSafetyCars(crew?.cars.orEmpty(), peers, now, record.demo)
                    rows.forEach { row -> SafetyCarRow(row.copy(rank = ranks.firstOrNull { it.car.id == row.car.id }?.rank), now, record.demo) }
                    if (safety.canCloseGroup && !closed && record.status != SafetyTripStatus.RECORDING) {
                        PrimaryButton("크루 집계 마감", safety::closeGroup, height = 48.dp, fontSize = 15.sp)
                        Text("마감 시 주행 중인 차는 미완료로 남고 완주 차량끼리 결과를 확정해요", color = CdColors.Gray500, fontSize = 12.sp)
                    }
                }
            }
            CdCard(Modifier.fillMaxWidth()) {
                SectionLabel("항목별 감점 기록 · 총 −${record.totalPenalty}점")
                if (record.events.isEmpty()) Text("측정된 구간에서 확정된 감점 항목이 없어요", color = CdColors.Gray600)
                record.events.asReversed().take(50).forEach { event ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${time(event.atMillis)}  ${event.type.label}", fontSize = 15.sp)
                        Text("−${event.penalty}점", fontWeight = FontWeight.Bold, color = CdColors.OrangeDark, fontSize = 15.sp)
                    }
                }
            }
            PrimaryButton("기록 화면으로", onBack, modifier = Modifier.fillMaxWidth(), height = 52.dp, fontSize = 17.sp)
        }
    }
}

@Composable
private fun SafetyRoad(rows: List<RankedSafetyCar>) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFFDFF3E6))) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.height / 190f
            drawRect(Color(0xFF3E4652), Offset(0f, 60 * scale), Size(size.width, 96 * scale))
            drawLine(Color.White, Offset(0f, 108 * scale), Offset(size.width, 108 * scale), 3 * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20 * scale, 18 * scale)))
            listOf(.12f, .46f, .8f).forEachIndexed { i, x -> drawCircle(Color(0xFF5DBB7A), (14 + i * 2) * scale, Offset(size.width * x, 30 * scale)) }
            listOf(.25f, .66f).forEach { x -> drawCircle(Color(0xFF6FC98A), 12 * scale, Offset(size.width * x, 179 * scale)) }
        }
        rows.take(4).forEachIndexed { i, row ->
            val x = (maxWidth - 144.dp) * ((i + .5f) / rows.size.coerceAtMost(4).coerceAtLeast(1)) + 12.dp
            Column(Modifier.offset(x = x, y = if (i % 2 == 0) 36.dp else 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${row.car.safetyName()}  ${row.rank?.let { "${if (row.provisional) "잠정 " else ""}${it}위" } ?: "측정 중"}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (row.car.isMe) CdColors.BlueDark else CdColors.Ink2,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(CdColors.White).padding(horizontal = 10.dp, vertical = 4.dp))
                CarBadge(row.car, size = 54)
            }
        }
    }
}

@Composable
private fun SafetyCarRow(row: RankedSafetyCar, now: Long, demo: Boolean) {
    val p = row.peer
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (row.car.isMe) CdColors.BlueLight else CdColors.White).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.rank?.toString() ?: "—", color = CdColors.BlueDark, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
        CarBadge(row.car, 26, bob = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.car.safetyName(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(when {
                p == null -> "기록 수신 대기"
                p.demo != demo -> "측정 모드 다름 · 비교 제외"
                p.status == SafetyTripStatus.STOPPED -> "미완료 · 순위 제외"
                p.status == SafetyTripStatus.RECORDING && now - p.updatedAtMillis > 15_000 -> "연결 대기 · 마지막 기록"
                row.provisional -> "잠정 · 급가속 ${p.accelerationCount} · 급정거 ${p.brakingCount}"
                row.rank == null -> "부분 평가 · 순위 대기"
                p.status == SafetyTripStatus.COMPLETED -> "도착 완료"
                else -> "급가속 ${p.accelerationCount} · 급정거 ${p.brakingCount}"
            }, color = CdColors.Gray500, fontSize = 11.sp)
        }
        Text(p?.let { "${it.score}" } ?: "—", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = if (p == null) CdColors.Gray400 else CdColors.Ink)
    }
}

@Composable
private fun SafetyMetrics(record: SafetyRecord) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SafetyEventType.entries.forEach { type ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CdColors.Gray50).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(type.label, fontSize = 13.sp, color = CdColors.Gray600, fontWeight = FontWeight.Bold)
                Text("${record.count(type)}회 · −${record.penalty(type)}점", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink2)
                Text(when (type) { SafetyEventType.ACCELERATION -> "1회 −2점"; SafetyEventType.BRAKING -> "1회 −3점" }, fontSize = 12.sp, color = CdColors.Gray500)
            }
        }
    }
}

@Composable
private fun QualityLine(record: SafetyRecord) {
    Text("속도 측정 ${(record.speedCoverage * 100).roundToInt()}% · 급가속 · 급정거 평가${if (!record.rankEligible) " · 확정 순위는 주행 1분 · 속도 측정 95% 이상" else ""}",
        fontSize = 12.sp, color = if (!record.rankEligible) CdColors.OrangeDark else CdColors.Green)
}

@Composable
private fun SafetyPodium(rows: List<RankedSafetyCar>) {
    val show = rows.take(4).let { if (it.size >= 3) listOf(it[1], it[0]) + it.drop(2) else it }
    Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        show.forEach { row ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.rank == 1) Icon(CdIcons.Trophy, null, tint = CdColors.Amber, modifier = Modifier.size(28.dp))
                CarBadge(row.car, 48, bob = false)
                Text(row.car.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().height(if (row.rank == 1) 112.dp else 82.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(if (row.rank == 1) CdColors.Blue else CdColors.BlueLight),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("${row.rank}위", color = if (row.rank == 1) CdColors.White else CdColors.BlueDark, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${row.peer?.score}점", color = if (row.rank == 1) CdColors.White else CdColors.BlueDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 이번 주 내 기록 (Member A, 2026-11-15): the week's finished trips and their average, and "저장된 내 차 기록 n개 보기 ›".
 * A tap on the card opens [SafetyWeekScreen] with all of this week's trips ([onOpen]).
 */
@Composable
private fun WeekCard(trips: List<SafetyRecord>, demo: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val full = trips.filter { it.status == SafetyTripStatus.COMPLETED && it.rankEligible }
    CdCard(modifier, onClick = if (trips.isNotEmpty()) onOpen else null, contentPadding = PaddingValues(18.dp), gap = 8.dp) {
        SectionLabel("이번 주 내 기록")
        Text(if (full.isEmpty()) "완료된 주행 기록이 아직 없어요" else "${full.size}회 완주 · 평균 ${full.map { it.score }.average().roundToInt()}점",
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdColors.Ink2)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("저장된 내 차 기록 ${trips.size}개 · ${if (demo) "모의 기록" else "실제 주행"}",
                fontSize = 13.sp, color = CdColors.Gray600, modifier = Modifier.weight(1f))
            if (trips.isNotEmpty()) Text("보기 ›", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdColors.BlueDark)
        }
    }
}

/** This week's trips of this car (demo or real, like board 16), newest first. */
private fun weekTrips(history: List<SafetyRecord>, demo: Boolean, now: Long): List<SafetyRecord> {
    val week = startOfWeek(now)
    return history.filter { it.demo == demo && it.startedAtMillis >= week && it.status != SafetyTripStatus.RECORDING }
}

/**
 * 이번 주 내 기록, opened from board 16's card (Member A, 2026-11-15): the week's summary and every trip of it. A trip
 * opens board 17 for it ([onOpen]) once the car has stopped, like 지난 내 차 기록. [onBack] goes back to board 16.
 */
@Composable
fun SafetyWeekScreen(safety: SafetyCoordinator, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val history by safety.history.collectAsStateWithLifecycle()
    val parked by safety.canFinish.collectAsStateWithLifecycle()
    val now by safety.clock.collectAsStateWithLifecycle()
    val trips = weekTrips(history, safety.demo, now)
    val full = trips.filter { it.status == SafetyTripStatus.COMPLETED && it.rankEligible }
    ScreenColumn(gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BackButton(onBack)
            ScreenHeader(eyebrow = "안전운전 대결 · ${if (safety.demo) "DEMO · 모의 기록" else "실제 주행"}", title = "이번 주 내 기록", titleSize = 28.sp)
        }
        CdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp), gap = 6.dp) {
            SectionLabel(if (full.isEmpty()) "완료된 주행 기록이 아직 없어요" else "${full.size}회 완주")
            if (full.isNotEmpty()) Text("평균 ${full.map { it.score }.average().roundToInt()}점", color = CdColors.BlueDark, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("저장된 내 차 기록 ${trips.size}개 · 완주한 기록만 평균에 들어가요", fontSize = 13.sp, color = CdColors.Gray500)
        }
        CdCard(Modifier.fillMaxWidth(), gap = 10.dp, contentPadding = PaddingValues(18.dp)) {
            if (trips.isEmpty()) Text("이번 주에 저장된 기록이 없어요", color = CdColors.Gray500, fontSize = 14.sp)
            trips.forEach { record ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = parked) { onOpen(record.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(record.destination, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${shortDate(record.startedAtMillis)} · ${when (record.status) { SafetyTripStatus.COMPLETED -> "도착 완료"; SafetyTripStatus.STOPPED -> "중도 종료"; else -> "중단된 주행" }} · 급가속 ${record.count(SafetyEventType.ACCELERATION)} · 급정거 ${record.count(SafetyEventType.BRAKING)}",
                            fontSize = 12.sp, color = CdColors.Gray500)
                    }
                    Text("${record.score}점  ›", color = CdColors.BlueDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (!parked && trips.isNotEmpty()) Text("자세한 기록은 정차 후 열 수 있어요", color = CdColors.Gray500, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HistoryCard(records: List<SafetyRecord>, parked: Boolean, onOpen: (String) -> Unit) {
    CdCard(Modifier.fillMaxWidth(), gap = 10.dp, contentPadding = PaddingValues(18.dp)) {
        SectionLabel("지난 내 차 기록")
        if (records.isEmpty()) Text("첫 주행을 마치면 이곳에 쌓여요", color = CdColors.Gray500, fontSize = 14.sp)
        records.take(10).forEach { record ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = parked) { onOpen(record.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${record.destination} · ${if (record.demo) "DEMO" else "실제 주행"}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${date(record.startedAtMillis)} · ${when(record.status) { SafetyTripStatus.COMPLETED -> "도착 완료"; SafetyTripStatus.STOPPED -> "중도 종료"; else -> "중단된 주행" }}", fontSize = 12.sp, color = CdColors.Gray500)
                }
                Text("${record.score}점  ›", color = CdColors.BlueDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (!parked && records.isNotEmpty()) Text("자세한 기록은 정차 후 열 수 있어요", color = CdColors.Gray500, fontSize = 12.sp)
    }
}

@Composable private fun SectionLabel(text: String) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink2) }
private fun sourceLabel(source: SafetySourceState, record: SafetyRecord): String = when {
    record.status != SafetyTripStatus.RECORDING -> "저장된 급가속 · 급정거 기록"
    source == SafetySourceState.PERMISSION_DENIED -> "차량 속도 권한 필요 · 평가 대기"
    source == SafetySourceState.UNAVAILABLE -> "차량 속도 연결 대기"
    source == SafetySourceState.CONNECTING -> "차량 속도 연결 중"
    source == SafetySourceState.WAITING -> "속도 데이터 대기 · 미측정 구간"
    else -> "급가속 · 급정거 평가 중"
}
private fun date(millis: Long): String = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(millis))
private fun shortDate(millis: Long): String = SimpleDateFormat("M.d (E)", Locale.KOREA).format(Date(millis))
private fun time(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
private fun Car.safetyName(): String = name + if (isMe && "(나)" !in name) " (나)" else ""

