package com.cardrop.drop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.AiTag
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.TodoNote
import com.cardrop.crew.CrewViewModel
import kotlinx.coroutines.delay

/**
 * Board 11 투표 (받는 차) · Owner: Member A.
 * When the vote ends, the app moves to board 14 on its own (see DropRouting).
 */
@Composable
fun VoteScreen(vm: CrewViewModel, dropId: String) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val drop = drops.firstOrNull { it.id == dropId } ?: return
    // Only the sender and the cars it was sent to answer this vote.
    val carCount = drop.voterIds(crew?.cars?.map { it.id }.orEmpty()).size

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dropId) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secondsLeft = ((drop.deadlineMillis - now) / 1000).coerceAtLeast(0)
    var picked by remember(dropId) { mutableStateOf(drop.votes[vm.myCarId] ?: drop.candidates.firstOrNull()?.id) }
    val alreadyVoted = vm.myCarId in drop.votes

    ScreenColumn {
        ScreenHeader(eyebrow = "${vm.carName(drop.fromCarId)}가 보낸 결정 드랍", title = drop.title) {
            Pill("%d:%02d".format(secondsLeft / 60, secondsLeft % 60))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AiTag()
            Text("${drop.votes.size}/$carCount 응답 · 무응답은 1순위로 처리돼요", style = MaterialTheme.typography.bodyLarge)
        }
        drop.candidates.forEachIndexed { index, candidate ->
            val votes = drop.votes.values.count { it == candidate.id }
            CdCard(
                modifier = Modifier.fillMaxWidth(),
                selected = picked == candidate.id,
                onClick = { if (!alreadyVoted) picked = candidate.id },
            ) {
                InfoRow(
                    title = "${index + 1}. ${candidate.place.name}",
                    subtitle = "${candidate.reason} · +${candidate.detourMinutes}분",
                    trailing = { Pill("${votes}표") },
                )
            }
        }
        TodoNote("일행 위치 지도, 돌림판 이스터에그 (board 11)")
        PrimaryButton(
            text = if (alreadyVoted) "응답했어요" else "응답하기",
            enabled = !alreadyVoted && picked != null,
            onClick = { picked?.let { vm.vote(dropId, it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("말로도 돼요 · “1번”, “아무 데나”, “패스”", style = MaterialTheme.typography.bodyMedium)
    }
}
