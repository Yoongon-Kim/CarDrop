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
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.drop.VoteRules

/** Board 14 결정 · 내비 경유지 변경 · Owner: Member B. */
@Composable
fun DecidedScreen(vm: DriveViewModel, dropId: String, onDone: () -> Unit) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val navRoute by vm.navRoute.collectAsStateWithLifecycle()
    val drop = drops.firstOrNull { it.id == dropId } ?: return
    val winner = drop.decidedCandidate ?: return
    val carIds = crew?.cars?.map { it.id }.orEmpty()

    ScreenColumn {
        ScreenHeader(
            eyebrow = "투표가 끝났어요 · ${carIds.size}대 중 ${VoteRules.agreeing(drop, carIds)}대 동의",
            title = "${winner.place.name}로 결정되었어요",
        )
        CdCard(modifier = Modifier.fillMaxWidth()) {
            InfoRow(title = winner.place.name, subtitle = winner.reason)
            Text(navRoute.lastAction ?: "내비에 경유지를 넣는 중…", style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                "지금 안내 시작",
                onClick = {
                    vm.startGuidance()
                    onDone()
                },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                "우리 차는 안 들르기",
                onClick = {
                    vm.skipStop()
                    onDone()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
