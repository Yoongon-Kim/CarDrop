package com.cardrop.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.core.ui.theme.CdColors

/**
 * The shared controls on one page. Open this file in Android Studio and pick "Split" or "Design"
 * at the top right of the editor to see it. Interactive mode lets you type in the text box.
 */
@Preview(widthDp = 760)
@Composable
private fun SharedControlsPreview() {
    CarDropTheme {
        Column(
            modifier = Modifier.background(CdColors.Gray100).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackButton(onClick = {})
                Text("AI가 휴게소 3곳을 골랐어요", style = MaterialTheme.typography.headlineLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CdChip("문구 추가", onClick = {}, style = ChipStyle.Violet, icon = CdIcons.Plus)
                CdChip("선택한 문구 고치기", onClick = {}, icon = CdIcons.Pencil)
                CdChip("삭제", onClick = {}, style = ChipStyle.Danger)
                CdChip("취소", onClick = {}, style = ChipStyle.White)
                CdChip("저장", onClick = {}, style = ChipStyle.PrimaryViolet)
                CdChip("보내기", onClick = {}, style = ChipStyle.Primary)
                CdChip("꺼짐", onClick = {}, enabled = false)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CdChip("말해서 추가", onClick = {}, style = ChipStyle.Blue, icon = CdIcons.Mic, modifier = Modifier.height(54.dp))
                var place by remember { mutableStateOf("") }
                CdTextField(place, { place = it }, placeholder = "다른 곳을 직접 입력", maxLength = 48, modifier = Modifier.weight(1f))
                CdChip("추가", onClick = {}, style = ChipStyle.White, modifier = Modifier.height(54.dp))
            }
            StatusText("듣고 있어요…")
            ErrorText("이미 있는 문구예요.")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(CdIcons.Back, CdIcons.Close, CdIcons.Plus, CdIcons.Pencil, CdIcons.Mic, CdIcons.Check).forEach {
                    Icon(it, contentDescription = null, tint = CdColors.Ink2)
                }
            }
        }
    }
}
