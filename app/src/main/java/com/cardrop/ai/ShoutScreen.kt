package com.cardrop.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader

private val presets = listOf("곧 도착해요", "먼저 가세요", "천천히 와요", "휴게소에서 봐요")

/** Board 09 일행에게 한마디 · Owner: lead. Sent as a SOCIAL drop: on screen only, never spoken. */
@Composable
fun ShoutScreen(vm: AiViewModel, initialText: String = "", onSent: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }

    ScreenColumn {
        ScreenHeader(eyebrow = "소셜 드랍 · 받는 차에는 음성 없이 화면에만 조용히 떠요", title = "일행에게 한마디")
        Text("자주 쓰는 한마디", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            presets.forEach { preset ->
                CdCard(modifier = Modifier.weight(1f), onClick = { vm.sendShout(preset, polish = false, onSent = onSent) }) {
                    Text(preset, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text("직접 말해서 보내기", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("“삼촌 차에 곧 도착한다고 전해줘” → AI가 짧게 다듬어 보내요") },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            "AI가 다듬어서 보내기",
            enabled = text.isNotBlank(),
            onClick = { vm.sendShout(text, polish = true, onSent = onSent) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("받는 차 · 크루 전체", style = MaterialTheme.typography.bodyMedium)
    }
}
