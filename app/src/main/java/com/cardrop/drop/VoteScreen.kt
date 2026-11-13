package com.cardrop.drop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.AiTag
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
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
    // 돌림판 is a small extra: only before answering, and only when there is something to choose between.
    val canSpin = !alreadyVoted && drop.candidates.size >= 2
    var wheelOpen by remember(dropId) { mutableStateOf(false) }

    // The sender's car, as the tag names it: "아빠 차의 제안". This car's own vote says "우리 차".
    val sender = if (drop.fromCarId == vm.myCarId) "우리 차" else vm.carName(drop.fromCarId)

    ScreenColumn {
        // Target lines 426-430 and 711-713: an orange tag, the big title and why these places, at the top.
        ScreenHeader(
            eyebrow = null,
            title = drop.title,
            tag = {
                Pill(
                    "${sender}의 제안 · ${drop.topic.voteTag()}",
                    background = CdColors.OrangeLight,
                    color = CdColors.OrangeDark,
                    fontSize = 13.sp,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            titleSize = 32.sp,
            titleLetterSpacing = TightLetterSpacing,
            subtitle = drop.message.ifBlank { null },
            subtitleSize = 15.sp,
            gap = 6.dp,
            verticalAlignment = Alignment.Top,
        ) {
            // The spin chip over the time left, at the right (target lines 432-436, 784). The chip opens the
            // wheel and closes it again, so it stays while the wheel is open.
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canSpin) {
                    CdChip(
                        "고민되면 돌려보기",
                        onClick = { wheelOpen = !wheelOpen },
                        style = ChipStyle.Dashed,
                        icon = CdIcons.Wheel,
                        height = 36.dp,
                        radius = 999.dp,
                        fontSize = 12.sp,
                        iconSize = 24.dp,
                        iconTint = Color.Unspecified, // the wheel keeps its three colours
                        contentPadding = PaddingValues(start = 6.dp, end = 12.dp),
                    )
                }
                Text(
                    "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                    color = CdColors.Alert,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 38.sp,
                )
            }
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
        if (canSpin && wheelOpen) {
            VoteRoulette(
                candidates = drop.candidates,
                // Only selects it; the driver still presses 응답하기 to send the vote.
                onPick = { candidateId ->
                    picked = candidateId
                    wheelOpen = false
                },
                onClose = { wheelOpen = false },
            )
        }
        TodoNote("일행 위치 지도 (board 11)")
        PrimaryButton(
            text = if (alreadyVoted) "응답했어요" else "응답하기",
            enabled = !alreadyVoted && picked != null,
            onClick = { picked?.let { vm.vote(dropId, it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("말로도 돼요 · “1번”, “아무 데나”, “패스”", style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the vote is about, after "{보낸 차}의 제안 · " in the tag (target lines 711-713: "식사", "주유"). */
private fun DropTopic.voteTag(): String = when (this) {
    DropTopic.REST -> "휴게소"
    DropTopic.FOOD -> "식사"
    DropTopic.FUEL -> "주유"
    DropTopic.DESTINATION -> "목적지 변경"
    DropTopic.CAFE -> "카페"
    DropTopic.CUSTOM -> "들를 곳"
    DropTopic.MESSAGE -> "한마디"
}
