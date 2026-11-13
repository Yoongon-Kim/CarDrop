package com.cardrop.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing

/*
 * The shared controls on a few pages. Open this file in Android Studio and pick "Split" or "Design"
 * at the top right of the editor to see them. Interactive mode lets you type in the text box.
 * Each page shows today's look first, then the size options with values from the 11-10 boards.
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
            // Size options: board 11's spin chip (dashed, round, the coloured wheel), board 01's 34-high
            // 바꾸기 (radius 10, 13 / 700) and a 46-high blue chip (15px).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CdChip(
                    "고민되면 돌려보기", onClick = {}, style = ChipStyle.Dashed, icon = CdIcons.Wheel,
                    height = 36.dp, radius = 999.dp, fontSize = 12.sp, iconSize = 24.dp, iconTint = Color.Unspecified,
                    contentPadding = PaddingValues(start = 6.dp, end = 12.dp),
                )
                CdChip(
                    "바꾸기", onClick = {}, height = 34.dp, radius = 10.dp, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                )
                CdChip("말로 읽어주기", onClick = {}, style = ChipStyle.Blue, icon = CdIcons.Mic, height = 46.dp, fontSize = 15.sp, iconSize = 18.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CdChip("말해서 추가", onClick = {}, style = ChipStyle.Blue, icon = CdIcons.Mic, height = 54.dp)
                var place by remember { mutableStateOf("") }
                CdTextField(place, { place = it }, placeholder = "다른 곳을 직접 입력", maxLength = 48, modifier = Modifier.weight(1f))
                CdChip("추가", onClick = {}, style = ChipStyle.White, height = 54.dp)
            }
            StatusText("듣고 있어요…")
            ErrorText("이미 있는 문구예요.")
            // Pills: today's 14sp, then the boards' usual 13px with 6 x 12 padding (board 11's orange tag).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("P · 정차 중에만 열려요")
                Pill(
                    "아빠 차의 제안 · 식사", background = CdColors.OrangeLight, color = CdColors.OrangeDark,
                    fontSize = 13.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                )
                AiTag()
                AiTag(small = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(CdIcons.Back, CdIcons.Close, CdIcons.Plus, CdIcons.Pencil, CdIcons.Mic, CdIcons.Check, CdIcons.Flag).forEach {
                    Icon(it, contentDescription = null, tint = CdColors.Ink2)
                }
                Icon(CdIcons.Search, contentDescription = null, tint = CdColors.Blue, modifier = Modifier.size(26.dp))
                Icon(CdIcons.Wheel, contentDescription = null, tint = Color.Unspecified)
            }
        }
    }
}

@Preview(widthDp = 760)
@Composable
private fun ButtonsAndCardsPreview() {
    CarDropTheme {
        Column(
            modifier = Modifier.background(CdColors.Gray100).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Today's buttons, and greyed out (enabled = false).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("출발", onClick = {}, modifier = Modifier.weight(1f))
                SecondaryButton("취소", onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("후보를 골라주세요", onClick = {}, modifier = Modifier.weight(1f), enabled = false)
                SecondaryButton("취소", onClick = {}, modifier = Modifier.weight(1f), enabled = false)
            }
            // Board 10's send button (78 high, radius 22, 21px) and board 03's 50-high one (radius 14, 15px).
            PrimaryButton("이 2곳으로 투표 보내기", onClick = {}, modifier = Modifier.fillMaxWidth(), height = 78.dp, radius = 22.dp, fontSize = 21.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("찾기", onClick = {}, height = 50.dp, radius = 14.dp, fontSize = 15.sp, horizontalPadding = 18.dp)
                PrimaryButton(
                    "받은 코드로 참여", onClick = {}, containerColor = CdColors.White, contentColor = CdColors.Ink2,
                    height = 50.dp, radius = 14.dp, fontSize = 15.sp, fontWeight = FontWeight.Bold, horizontalPadding = 18.dp,
                )
                SecondaryButton("다시 시도", onClick = {}, height = 58.dp, radius = 16.dp, fontSize = 16.sp)
            }
            // Cards: today's (radius 26, padding 22 x 18, gap 10), then board 05's (radius 24, padding 24, gap 10).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CdCard(Modifier.weight(1f)) {
                    Text("오늘의 카드", style = MaterialTheme.typography.titleMedium)
                    Text("radius 26 · padding 22 x 18", style = MaterialTheme.typography.bodyMedium)
                }
                CdCard(Modifier.weight(1f), radius = 24.dp, contentPadding = PaddingValues(24.dp)) {
                    Text("보드 05의 카드", style = MaterialTheme.typography.titleMedium)
                    Text("radius 24 · padding 24", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Soft shadows from the boards' CSS: the navigator's turn banner and board 02's blue search box.
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(200.dp, 64.dp)
                        .softShadow(RoundedCornerShape(20.dp), Color.Black.copy(alpha = 0.15f), blur = 18.dp, y = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CdColors.NavBanner),
                )
                Box(
                    modifier = Modifier
                        .size(200.dp, 64.dp)
                        .softShadow(RoundedCornerShape(20.dp), CdColors.Blue.copy(alpha = 0.15f), blur = 20.dp, y = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CdColors.White),
                )
            }
            // A car that stands still (board 15's opponent chips) next to today's bobbing one.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CarBadge(PREVIEW_CAR)
                CarBadge(PREVIEW_CAR, size = 24, bob = false)
            }
        }
    }
}

@Preview(widthDp = 760, heightDp = 420)
@Composable
private fun HeadersAndLayoutPreview() {
    CarDropTheme {
        Row(modifier = Modifier.background(CdColors.Gray100)) {
            Column(
                modifier = Modifier.weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Today's header.
                ScreenHeader(eyebrow = "정차 중 · 크루 전체", title = "일행이랑 한 판 할까요?") { Pill("P") }
                // Board 04: eyebrow 14 / 700, gap 2, title 28 with -0.02em, centred with the right side.
                ScreenHeader(
                    eyebrow = "강릉 여행 · 4대 함께 · 음성 우선", title = "모두 잘 오고 있어요",
                    eyebrowWeight = FontWeight.Bold, titleSize = 28.sp, titleLetterSpacing = TightLetterSpacing, gap = 2.dp,
                    verticalAlignment = Alignment.CenterVertically,
                ) { Pill("음성 모드") }
                // Board 03: no eyebrow, a subtitle under the title, no gap.
                ScreenHeader(eyebrow = null, title = "코드로 참여", subtitle = "주행 중엔 “코드 482917”처럼 말해서 넣어요", gap = 0.dp)
                // Board 11: a tag pill above, title 32 with -0.02em, a 15px subtitle, gap 6, lined up at the top.
                ScreenHeader(
                    eyebrow = null, title = "어디서 밥 먹을까요?", titleSize = 32.sp, titleLetterSpacing = TightLetterSpacing,
                    subtitle = "AI가 4대 모두 가기 좋은 곳으로 골랐어요", subtitleSize = 15.sp, gap = 6.dp,
                    verticalAlignment = Alignment.Top,
                    tag = {
                        Pill(
                            "아빠 차의 제안 · 식사", background = CdColors.OrangeLight, color = CdColors.OrangeDark,
                            fontSize = 13.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        )
                    },
                ) { Pill("1:42") }
            }
            // ScreenColumn's bottom slot: the button sits at the bottom while the content is short.
            Box(modifier = Modifier.width(260.dp).height(420.dp).background(CdColors.Gray50)) {
                ScreenColumn(bottom = { PrimaryButton("응답하기", onClick = {}, modifier = Modifier.fillMaxWidth()) }) {
                    CdCard(Modifier.fillMaxWidth()) { Text("짧은 내용", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

private val PREVIEW_CAR = Car(id = "dad", name = "아빠 차", color = CarColor.BLUE, isMe = true)
