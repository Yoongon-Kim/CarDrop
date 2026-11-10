package com.cardrop.crew

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Place
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.TodoNote

/**
 * Board 02 목적지 검색 · Owner: Member A.
 * Week 1 searches a fixed list (DemoData.destinations), not a real place-search API.
 */
@Composable
fun DestinationSearchScreen(vm: CrewViewModel, onPicked: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results: List<Place> = DemoData.destinations.filter { query.isBlank() || query.trim() in it.name }

    ScreenColumn {
        ScreenHeader(eyebrow = "크루 목적지", title = "목적지를 검색해 주세요")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("목적지 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TodoNote("말로 검색 (VoiceService.listen)")
        results.forEach { place ->
            CdCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.setDestination(place)
                    onPicked()
                },
            ) {
                InfoRow(title = place.name, subtitle = place.detail)
            }
        }
    }
}
