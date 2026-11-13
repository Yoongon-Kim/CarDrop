package com.cardrop.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors

/** Board 03 코드로 참여 · Owner: Member A. With the fake, code 482917 always works. */
@Composable
fun JoinCrewScreen(vm: CrewViewModel, onBack: () -> Unit, onJoined: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val join by vm.join.collectAsStateWithLifecycle()

    ScreenColumn {
        // Design lines 177-179: the back button, then the title with the grey line under it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            ScreenHeader(
                eyebrow = null,
                title = "코드로 참여",
                subtitle = "주행 중엔 “코드 482917”처럼 말해서 넣어요",
                gap = 0.dp,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("6자리 코드") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(280.dp),
            )
            PrimaryButton("찾기", onClick = { vm.joinCrew(code) }, enabled = code.length == 6)
        }

        when (val state = join) {
            CrewViewModel.JoinState.Idle -> Unit
            CrewViewModel.JoinState.Searching -> Text("크루를 찾는 중…", style = MaterialTheme.typography.bodyLarge)
            CrewViewModel.JoinState.NotFound -> ErrorText("그 코드의 크루가 없어요")
            is CrewViewModel.JoinState.Found -> CdCard(modifier = Modifier.fillMaxWidth()) {
                Text("크루를 찾았어요", style = MaterialTheme.typography.labelMedium)
                Text(state.crew.name, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${vm.carName(state.crew.hostCarId)} 크루 · ${state.crew.cars.size}대 참여 중 · ${state.crew.destination.name}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                // withRo() picks 로 or 으로 for the name: "강릉 경포해변으로", "속초 해수욕장으로", "서울로".
                val toDestination = state.crew.destination.name.withRo()
                Text("$toDestination 안내할까요?", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("신촌역 들렀다 가기", onClick = onJoined)
                    PrimaryButton("바로 $toDestination", onClick = onJoined)
                }
                TodoNote("신촌역 경유 버튼은 지금 경로를 경유지로 넣어야 해요 (board 03)")
                Text(
                    "참여하면 도착할 때까지 일행에게 내 위치가 보여요 · 방장이 수락하면 연결돼요",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
