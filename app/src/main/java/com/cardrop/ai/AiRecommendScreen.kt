package com.cardrop.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.AiTag
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors

/** Board 10 AI 추천 (휴게소 · 식사 · 주유) · Owner: lead. */
@Composable
fun AiRecommendScreen(vm: AiViewModel, topic: DropTopic, onSent: (Drop) -> Unit) {
    LaunchedEffect(topic) { vm.loadCandidates(topic) }
    val state by vm.candidates.collectAsStateWithLifecycle()

    ScreenColumn {
        ScreenHeader(eyebrow = "AI 추천", title = vm.topicTitle(topic)) { AiTag() }
        Text(
            "AI가 본 정보 · 4대 모두 지나가는 경로 · 도착 시간 차이 · 연료 · 연속 운전 시간 · 리뷰 평점",
            style = MaterialTheme.typography.bodyMedium,
        )
        when (val s = state) {
            AiViewModel.Loadable.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("AI가 고르는 중…", style = MaterialTheme.typography.bodyLarge)
            }
            is AiViewModel.Loadable.Failed -> {
                Text(s.message, color = CdColors.OrangeDark)
                SecondaryButton("다시 시도", onClick = { vm.loadCandidates(topic) })
            }
            is AiViewModel.Loadable.Ready -> {
                s.value.forEachIndexed { index, candidate ->
                    CdCard(modifier = Modifier.fillMaxWidth()) {
                        InfoRow(
                            title = "${index + 1}. ${candidate.place.name}",
                            subtitle = "${candidate.place.detail}\n${candidate.reason}",
                            trailing = { Pill("+${candidate.detourMinutes}분") },
                        )
                    }
                }
                PrimaryButton(
                    "이 ${s.value.size}곳으로 투표 보내기",
                    onClick = { vm.sendVote(topic, s.value, onSent) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
