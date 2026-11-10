package com.cardrop.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors

/**
 * Board 15 게임 (정차 중) · Owner: lead.
 * Week 1 builds the AI local quiz only (no omok or chess). It opens only in P.
 */
@Composable
fun QuizScreen(vm: AiViewModel) {
    val snapshot by vm.vehicleSnapshot.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snapshot.isParked) { if (snapshot.isParked) vm.loadQuiz() }

    ScreenColumn {
        ScreenHeader(eyebrow = "정차 중 · 크루 전체", title = "일행이랑 한 판 할까요?") {
            Pill("P · 정차 중에만 열려요")
        }
        if (!snapshot.isParked) {
            Text("D로 바꾸면 게임이 멈추고 다음 정차 때 이어서 해요", style = MaterialTheme.typography.bodyLarge)
            return@ScreenColumn
        }
        when (val q = quiz) {
            AiViewModel.Loadable.Loading -> CircularProgressIndicator()
            is AiViewModel.Loadable.Failed -> Text(q.message, color = CdColors.OrangeDark)
            is AiViewModel.Loadable.Ready -> {
                val question = q.value
                Text("지역 퀴즈 · AI가 지금 위치로 출제", style = MaterialTheme.typography.labelMedium)
                Text(question.question, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    question.tiles.forEach { tile ->
                        Box(
                            modifier = Modifier
                                .size(width = 96.dp, height = 104.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(CdColors.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tile, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                question.answers.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { answer ->
                            val chosen = picked == answer
                            CdCard(modifier = Modifier.weight(1f), selected = chosen, onClick = { picked = answer }) {
                                Text(answer, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                picked?.let {
                    val correct = it == question.correctAnswer
                    Text(
                        if (correct) "정답! 먼저 맞힌 차가 점수를 받아요" else "아쉬워요 · 정답은 ${question.correctAnswer}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
