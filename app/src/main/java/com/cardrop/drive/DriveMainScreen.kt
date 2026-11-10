package com.cardrop.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Gear
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors

/** Fuel at or below this percent opens the board 05 warning. */
const val LOW_FUEL_PERCENT = 15

/** Board 04 주행 메인 · Owner: Member B. */
@Composable
fun DriveMainScreen(
    vm: DriveViewModel,
    onQuickDrop: (DropTopic) -> Unit,
    onOpenDropSend: () -> Unit,
    onOpenInbox: () -> Unit,
    onFuelWarning: () -> Unit,
    onSpeak: () -> Unit,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val cars = crew?.cars.orEmpty()
    val myEta = positions[vm.myCarId]?.etaMinutes

    ScreenColumn {
        ScreenHeader(
            eyebrow = "${crew?.name ?: "크루 없음"} · ${cars.size}대 함께 · 음성 우선",
            title = if (myEta != null) "${crew?.destination?.name}까지 ${myEta / 60}시간 ${myEta % 60}분" else "출발 준비 중",
        ) {
            if (snapshot.gear == Gear.DRIVE) Pill("주행 중 · 음성 모드") else Pill("정차 중")
        }

        val fuel = snapshot.fuelPercent
        if (fuel != null && fuel <= LOW_FUEL_PERCENT) {
            CdCard(modifier = Modifier.fillMaxWidth(), onClick = onFuelWarning) {
                InfoRow(
                    title = "연료 $fuel% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "-"}km",
                    subtitle = "눌러서 AI 주유소 추천 받기",
                    trailing = { Pill("연료 경고", CdColors.OrangeLight, CdColors.OrangeDark) },
                )
            }
        }

        CdCard(modifier = Modifier.fillMaxWidth()) {
            Text("일행 위치", style = MaterialTheme.typography.titleMedium)
            cars.forEach { car ->
                val p = positions[car.id]
                InfoRow(
                    title = car.name,
                    subtitle = if (p != null) "${p.kmToDestination.toInt()}km 남음 · ${p.etaMinutes}분 후 도착" else "위치 기다리는 중",
                    leading = { CarBadge(car, size = 40) },
                )
            }
            TodoNote("안전 운전 리더보드, 일행 차 그림 (board 04)")
        }

        Text("퀵 드랍 · 누르면 바로 크루 전체에", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("휴게소", onClick = { onQuickDrop(DropTopic.REST) }, modifier = Modifier.weight(1f))
            SecondaryButton("식사", onClick = { onQuickDrop(DropTopic.FOOD) }, modifier = Modifier.weight(1f))
            SecondaryButton("주유", onClick = { onQuickDrop(DropTopic.FUEL) }, modifier = Modifier.weight(1f))
            SecondaryButton("편지함", onClick = onOpenInbox, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("말로 제안해요", onClick = onSpeak, modifier = Modifier.weight(1f))
            SecondaryButton("드랍하기", onClick = onOpenDropSend, modifier = Modifier.weight(1f))
        }
        Text("스티어링 음성 버튼으로도 말할 수 있어요", style = MaterialTheme.typography.bodyMedium)
        TodoNote("D단에서는 터치 제한, 음성만 (FeatureMap · 안전 설계)")
    }
}
