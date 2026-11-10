package com.cardrop.drop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.TodoNote
import com.cardrop.crew.CrewViewModel

/**
 * Board 08 드랍 보내기 · Owner: Member A.
 * Picking a decision topic opens board 10 (AI candidates); 한마디 opens board 09.
 */
@Composable
fun DropSendScreen(
    vm: CrewViewModel,
    onPickTopic: (DropTopic) -> Unit,
    onShout: () -> Unit,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf("all") }

    ScreenColumn {
        ScreenHeader(eyebrow = "누르면 바로 다음 단계로 넘어가요", title = "누구에게 보낼까요?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            CdCard(modifier = Modifier.weight(1f), selected = target == "all", onClick = { target = "all" }) {
                Text("크루 전체", style = MaterialTheme.typography.titleMedium)
            }
            crew?.cars.orEmpty().filterNot { it.isMe }.forEach { car ->
                CdCard(modifier = Modifier.weight(1f), selected = target == car.id, onClick = { target = car.id }) {
                    CarBadge(car, size = 36)
                    Text(car.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        TodoNote("한 차에만 보내기는 DropDraft에 대상 차 목록이 필요해요")

        Text("무엇을 보낼까요?", style = MaterialTheme.typography.titleLarge)
        Text("결정 드랍 · 음성으로 안내돼요", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TopicCard("휴게소 가자", "AI가 3곳 추천", Modifier.weight(1f)) { onPickTopic(DropTopic.REST) }
            TopicCard("밥 먹자", "식당 · 휴게소 맛집", Modifier.weight(1f)) { onPickTopic(DropTopic.FOOD) }
            TopicCard("주유하자", "가는 길 주유소", Modifier.weight(1f)) { onPickTopic(DropTopic.FUEL) }
        }
        Text("소셜 드랍 · 화면에만 조용히", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TopicCard("일행에게 한마디", "자주 쓰는 말", Modifier.weight(1f), onClick = onShout)
            TopicCard("게임 초대", "정차 시 시작", Modifier.weight(1f)) {}
            TopicCard("노래 드랍", "이번 주는 빼요", Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun TopicCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    CdCard(modifier = modifier, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
