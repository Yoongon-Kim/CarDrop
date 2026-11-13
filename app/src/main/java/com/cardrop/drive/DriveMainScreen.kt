package com.cardrop.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors

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
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val input by vm.inputMode.collectAsStateWithLifecycle()
    val warning by vm.fuelWarningPending.collectAsStateWithLifecycle()
    LaunchedEffect(warning, crew?.code) {
        if (warning && crew != null) {
            vm.acknowledgeFuelWarning()
            onFuelWarning()
        }
    }
    val touch = input.allowsTouch && crew != null
    val cars = crew?.cars.orEmpty()
    val myEta = positions[vm.myCarId]?.etaMinutes

    ScreenColumn {
        ScreenHeader(
            eyebrow = "${crew?.name ?: "크루 없음"} · ${cars.size}대 함께 · 음성 우선",
            title = if (myEta != null) "${navRoute.destination?.name ?: crew?.destination?.name}까지 ${myEta / 60}시간 ${myEta % 60}분" else "출발 준비 중",
        ) {
            Pill(input.label)
        }

        val fuel = snapshot.fuelPercent
        if (fuel != null && fuel <= LOW_FUEL_PERCENT) {
            CdCard(modifier = Modifier.fillMaxWidth(), onClick = if (touch) onFuelWarning else null) {
                InfoRow(
                    title = "연료 $fuel% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "—"}km",
                    subtitle = if (touch) "눌러서 주유소 추천 받기" else "주유소 추천은 P단에서 확인할 수 있어요",
                    trailing = { Pill("연료 경고", CdColors.OrangeLight, CdColors.OrangeDark) },
                )
            }
        }

        CrewDriveOverview(cars, positions, vm.myCarId, snapshot.fuelPercent)

        Text("퀵 드랍 · 보낼 곳을 골라요", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("휴게소", onClick = { onQuickDrop(DropTopic.REST) }, modifier = Modifier.weight(1f), enabled = touch)
            SecondaryButton("식사", onClick = { onQuickDrop(DropTopic.FOOD) }, modifier = Modifier.weight(1f), enabled = touch)
            SecondaryButton("주유", onClick = { onQuickDrop(DropTopic.FUEL) }, modifier = Modifier.weight(1f), enabled = touch)
            SecondaryButton("편지함", onClick = onOpenInbox, modifier = Modifier.weight(1f), enabled = touch)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("말로 제안해요", onClick = onSpeak, modifier = Modifier.weight(1f), enabled = crew != null)
            SecondaryButton("드랍하기", onClick = onOpenDropSend, modifier = Modifier.weight(1f), enabled = touch)
        }
        if (!input.allowsTouch) Text("화면 조작은 P단에서 사용할 수 있어요", style = MaterialTheme.typography.bodyMedium)
        Text("내 차 연료 ${snapshot.fuelPercent ?: "—"}% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "—"}km · 연속 운전 ${snapshot.drivingMinutes ?: "—"}분")
    }
}
