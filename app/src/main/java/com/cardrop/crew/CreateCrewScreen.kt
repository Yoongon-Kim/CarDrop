package com.cardrop.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors

/** Board 01 크루 만들기 (방장) · Owner: Member A. */
@Composable
fun CreateCrewScreen(
    vm: CrewViewModel,
    onSearchDestination: () -> Unit,
    onJoinWithCode: () -> Unit,
    onDepart: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.ensureCrew() }
    val crew by vm.crew.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()

    ScreenColumn {
        ScreenHeader(eyebrow = "출발 전 · 정차 중", title = "함께 갈 차를 모아요") {
            SecondaryButton("받은 코드로 참여", onClick = onJoinWithCode)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            CdCard(modifier = Modifier.weight(1f), onClick = onSearchDestination) {
                Text("목적지", style = MaterialTheme.typography.labelMedium)
                Text(crew?.destination?.name ?: "목적지를 골라 주세요", style = MaterialTheme.typography.titleLarge)
                // "강원 강릉시 · 해수욕장 · 약 62km · 1시간 5분": the road distance and time come from the navigator.
                val detail = listOfNotNull(crew?.destination?.detail?.takeIf { it.isNotBlank() }, trip?.text)
                Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Text("입력하면 내비 목적지에 바로 반영돼요 · 일행 모두 같은 목적지로", style = MaterialTheme.typography.bodyMedium)
            }
            CdCard(modifier = Modifier.weight(1f)) {
                Text("참여 코드", style = MaterialTheme.typography.labelMedium)
                Text(
                    crew?.code?.chunked(3)?.joinToString(" ") ?: "------",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = CdColors.Ink,
                )
                TodoNote("말로 읽어주기 · 링크 문자로 보내기 (board 01)")
            }
        }
        CdCard(modifier = Modifier.fillMaxWidth()) {
            val cars = crew?.cars.orEmpty()
            Text("크루 멤버 · ${cars.count { it.connected }}/${cars.size}대 연결", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cars.forEach { car ->
                    InfoRow(
                        title = car.name,
                        subtitle = if (car.isMe) "방장" else if (car.connected) "코드로 참여" else "초대 보냄 · 응답 기다리는 중",
                        leading = { CarBadge(car) },
                        trailing = {
                            if (car.connected) {
                                Pill("연결됨")
                            } else {
                                Pill("기다리는 중", background = CdColors.OrangeLight, color = CdColors.OrangeDark)
                            }
                        },
                    )
                }
            }
        }
        PrimaryButton("다 모이면 출발하기", onClick = onDepart, modifier = Modifier.fillMaxWidth())
        Text("“출발하자”라고 말해도 돼요", style = MaterialTheme.typography.bodyMedium)
    }
}
