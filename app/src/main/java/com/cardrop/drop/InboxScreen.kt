package com.cardrop.drop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel

/** Board 07 드랍 편지함 · Owner: Member A. The design shows it as a half sheet over board 04. */
@Composable
fun InboxScreen(vm: CrewViewModel, onOpenDrop: (Drop) -> Unit) {
    val drops by vm.drops.collectAsStateWithLifecycle()

    ScreenColumn {
        ScreenHeader(eyebrow = "놓친 결정 드랍은 시간이 지나면 1순위로 자동 처리돼요", title = "드랍 편지함")
        TodoNote("절반 시트로 바꾸기 (board 07)")
        if (drops.isEmpty()) Text("아직 받은 드랍이 없어요", style = MaterialTheme.typography.bodyLarge)
        drops.asReversed().forEach { drop ->
            CdCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenDrop(drop) }) {
                InfoRow(
                    title = drop.title,
                    subtitle = "${vm.carName(drop.fromCarId)} · ${drop.message}",
                    trailing = {
                        when {
                            drop.kind == DropKind.SOCIAL -> Pill("소셜", CdColors.Gray100, CdColors.Gray600)
                            drop.isDecided -> Pill("결정됨")
                            drop.fromCarId != vm.myCarId && vm.myCarId !in drop.votes ->
                                Pill("응답 필요", CdColors.OrangeLight, CdColors.OrangeDark)
                            else -> Pill("투표 중")
                        }
                    },
                )
            }
        }
    }
}
