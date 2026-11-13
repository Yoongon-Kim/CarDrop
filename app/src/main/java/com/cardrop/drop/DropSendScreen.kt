package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel

/**
 * Board 08 드랍 보내기 · Owner: Member A.
 * Choose the receiving cars (크루 전체, or one or more cars), then what to send: a decision topic
 * opens board 10 (AI candidates), 한마디 opens board 09. Both get the chosen cars (empty = whole crew).
 * 게임 초대 opens board 15 ([onGames]), where the driver picks the game and who to play. The ✕ at the top
 * right ([onClose]) goes back to 홈 (board 04).
 */
@Composable
fun DropSendScreen(
    vm: CrewViewModel,
    onPickTopic: (topic: DropTopic, toCarIds: List<String>) -> Unit,
    onShout: (toCarIds: List<String>) -> Unit,
    onGames: () -> Unit,
    onClose: () -> Unit,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val others = crew?.cars.orEmpty().filterNot { it.isMe }
    // The chosen cars; empty = 크루 전체. Choosing every car switches back to 크루 전체.
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val toCarIds = others.map { it.id }.filter { it in picked }

    ScreenColumn {
        // The title alone, with a ✕ at the right (target lines 364-366).
        ScreenHeader(eyebrow = null, title = "누구에게 보낼까요?", gap = 0.dp, verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClick = onClose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            CdCard(modifier = Modifier.weight(1f), selected = picked.isEmpty(), onClick = { picked = emptySet() }) {
                Text("크루 전체", style = MaterialTheme.typography.titleMedium)
            }
            others.forEach { car ->
                CdCard(
                    modifier = Modifier.weight(1f),
                    selected = car.id in picked,
                    onClick = {
                        val next = if (car.id in picked) picked - car.id else picked + car.id
                        picked = if (next.size == others.size) emptySet() else next
                    },
                ) {
                    CarBadge(car, size = 36)
                    Text(car.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            "받는 차 · ${crew?.recipientLabel(toCarIds, vm.myCarId) ?: "크루 전체"} · 여러 대를 고를 수 있어요",
            style = MaterialTheme.typography.labelMedium,
        )

        Text("무엇을 보낼까요?", style = MaterialTheme.typography.titleLarge)
        Text("결정 드랍 · 음성으로 안내돼요", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TopicCard("휴게소 가자", "AI가 3곳 추천", Modifier.weight(1f)) { onPickTopic(DropTopic.REST, toCarIds) }
            TopicCard("밥 먹자", "식당 · 휴게소 맛집", Modifier.weight(1f)) { onPickTopic(DropTopic.FOOD, toCarIds) }
            TopicCard("주유하자", "가는 길 주유소", Modifier.weight(1f)) { onPickTopic(DropTopic.FUEL, toCarIds) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TopicCard("카페 가자", "경포 근처 카페", Modifier.weight(1f)) { onPickTopic(DropTopic.CAFE, toCarIds) }
            TopicCard("직접 추가", "원하는 곳을 골라요", Modifier.weight(1f)) { onPickTopic(DropTopic.CUSTOM, toCarIds) }
            Spacer(Modifier.weight(1f)) // keeps the cards the same width as the row above
        }
        Text("소셜 드랍 · 화면에만 조용히", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TopicCard("일행에게 한마디", "자주 쓰는 말", Modifier.weight(1f)) { onShout(toCarIds) }
            TopicCard("게임 초대", "정차 시 시작", Modifier.weight(1f), onClick = onGames)
            TopicCard("노래 드랍", "이번 주는 빼요", Modifier.weight(1f)) {}
        }
    }
}

/** The white square ✕ at the top right: 54 x 54, corners 16, a 24 px cross (target line 366). */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CdColors.White)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CdIcons.Close, contentDescription = "닫기", tint = CdColors.Ink2, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TopicCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    CdCard(modifier = modifier, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
