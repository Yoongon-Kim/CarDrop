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
import com.cardrop.core.ui.AiTag
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton

/** Board 05 연료 경고 · 내 차 (AI 주유소 추천) · Owner: Member B. */
@Composable
fun FuelMeScreen(vm: DriveViewModel, onDone: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()

    ScreenColumn {
        ScreenHeader(
            eyebrow = "연료 경고 · Vehicle SDK 연료 데이터로 판단했어요",
            title = "연료 ${snapshot.fuelPercent ?: "-"}% · 주행 가능 ${snapshot.rangeKm?.toInt() ?: "-"}km",
        )
        CdCard(modifier = Modifier.fillMaxWidth()) {
            AiTag()
            Text("“우리 지금 주유소 가야 할 것 같아요.\n다른 차들에게도 알릴까요?”", style = MaterialTheme.typography.headlineLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                "응, 알려줘",
                onClick = { vm.alertCrewAboutFuel { onDone() } },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                "아니, 혼자 갈게",
                onClick = {
                    vm.goToStationAlone()
                    onDone()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "“응”, “알려줘”라고 말해도 돼요 · 어느 쪽이든 AI가 주유소를 골라드려요\n가는 길에 있고, 남은 연료로 갈 수 있는 곳만 골랐어요",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Board 06 연료 알림 받은 차 · 같이 갈래요? · Owner: Member B. */
@Composable
fun FuelThemScreen(vm: DriveViewModel, dropId: String, carName: (String) -> String, onDone: () -> Unit) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val drop = drops.firstOrNull { it.id == dropId } ?: return
    val station = drop.candidates.firstOrNull()

    ScreenColumn {
        ScreenHeader(eyebrow = "${carName(drop.fromCarId)} · ${drop.message}", title = drop.title)
        if (station != null) {
            CdCard(modifier = Modifier.fillMaxWidth()) {
                AiTag()
                InfoRow(title = station.place.name, subtitle = "${station.reason} · +${station.detourMinutes}분")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("가던 길 갈래요", onClick = onDone, modifier = Modifier.weight(1f))
            PrimaryButton(
                "같이 갈래요",
                onClick = {
                    vm.joinFuelStop(drop)
                    onDone()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
