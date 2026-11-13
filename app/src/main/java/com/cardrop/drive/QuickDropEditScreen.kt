package com.cardrop.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.core.ui.theme.CdColors
import kotlin.math.roundToInt

/**
 * 퀵 드랍 편집 · Owner: lead. Opened from board 04's 편집 or an empty slot. As on the live canvas (`quickEdit`,
 * `git show 1caa955:design/FinalApp.dc.html` lines 324-344): the driver ticks up to four drops, numbered in the
 * order they will sit on board 04. 홈에 저장하기 keeps them on this device ([onSave]); 취소 and 뒤로 leave the old
 * ones ([onCancel]). The drops cut this week are greyed out (an app choice; the canvas has them all).
 */
@Composable
fun QuickDropEditScreen(saved: List<QuickDrop>, onCancel: () -> Unit, onSave: (List<QuickDrop>) -> Unit) {
    // The ticks so far; board 04 changes only on 홈에 저장하기.
    var draft by remember { mutableStateOf(saved) }
    var full by remember { mutableStateOf(false) }

    // The two buttons pinned to the bottom, 10 apart, 취소 a third of the width (canvas `margin-top: auto`, 1 : 2).
    ScreenColumn(bottom = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                "취소",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                containerColor = CdColors.White,
                contentColor = CdColors.Ink2,
                height = 68.dp,
                fontSize = 17.sp,
            )
            PrimaryButton("홈에 저장하기", onClick = { onSave(draft) }, modifier = Modifier.weight(2f), height = 68.dp, fontSize = 18.sp)
        }
    }) {
        // The back button, the title (30/800) with its grey line, and "2 / 4" at the right, all centred, 12 apart.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onCancel)
            ScreenHeader(
                eyebrow = null,
                title = "퀵 드랍 편집",
                subtitle = "홈에 둘 드랍을 최대 ${QuickDropRules.SLOTS}개 골라요 · 고른 순서대로 놓여요",
                gap = 0.dp,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Pill(
                    "${draft.size} / ${QuickDropRules.SLOTS}",
                    fontSize = 15.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        // Every drop, three to a row, in a white card (radius 26, padding 22, 10 between them).
        CdCard(radius = 26.dp, contentPadding = PaddingValues(22.dp), gap = 10.dp) {
            QuickDrop.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { drop ->
                        val slot = draft.indexOf(drop).takeIf { it >= 0 }?.plus(1)
                        OptionTile(drop, slot, Modifier.weight(1f)) {
                            when (val result = QuickDropRules.pick(draft, drop)) {
                                is QuickDropRules.Pick.Changed -> {
                                    draft = result.drops
                                    full = false
                                }
                                QuickDropRules.Pick.Full -> full = true
                            }
                        }
                    }
                }
            }
        }
        if (full) ErrorText(QuickDropRules.FULL_HINT)
    }
}

/**
 * One drop in the editor: its icon (46, a 22 px line icon), the name (17/800) and a circle at the right, 12 apart,
 * at least 80 high with corners 20. Ticked: a blue edge on light blue and the slot number in a blue circle.
 * A cut drop is faded to 40% and can't be ticked.
 */
@Composable
private fun OptionTile(drop: QuickDrop, slot: Int?, modifier: Modifier, onTap: () -> Unit) {
    val ticked = slot != null
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .heightIn(min = 80.dp)
            .alpha(if (drop.available) 1f else 0.4f)
            .clip(shape)
            .background(if (ticked) CdColors.BlueTint else CdColors.White)
            .border(2.dp, if (ticked) CdColors.Blue else CdColors.Gray100, shape)
            .toggleable(value = ticked, enabled = drop.available, role = Role.Checkbox, onValueChange = { onTap() })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickDropIcon(drop, size = 46.dp, iconSize = 22.dp)
        Text(
            drop.label,
            color = CdColors.Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (slot != null) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(CdColors.Blue), contentAlignment = Alignment.Center) {
                Text("$slot", color = CdColors.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            Box(Modifier.size(30.dp).border(2.dp, CdColors.Gray300, CircleShape))
        }
    }
}

/**
 * A quick drop's line icon on its light square: [size] wide, corners a third of it, as the canvas's `icStyle`
 * (38 on board 04's tiles, 46 in the editor). The colours are the canvas's `CLR`.
 */
@Composable
internal fun QuickDropIcon(drop: QuickDrop, size: Dp, iconSize: Dp) {
    val look = drop.look()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value / 3).roundToInt().dp))
            .background(look.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(iconSize))
    }
}

private class QuickDropLook(val icon: ImageVector, val background: Color, val color: Color)

private fun QuickDrop.look(): QuickDropLook = when (this) {
    QuickDrop.REST -> QuickDropLook(CdIcons.Rest, CdColors.BlueLight, CdColors.BlueDark)
    QuickDrop.FOOD -> QuickDropLook(CdIcons.Food, CdColors.OrangeLight, CdColors.OrangeDeep)
    QuickDrop.FUEL -> QuickDropLook(CdIcons.Fuel, CdColors.YellowLight, CdColors.YellowDeep)
    QuickDrop.SHOUT -> QuickDropLook(CdIcons.Shout, CdColors.VioletLight, CdColors.VioletDark)
    QuickDrop.SONG -> QuickDropLook(CdIcons.Music, SongTint, SongInk)
    QuickDrop.GAME -> QuickDropLook(CdIcons.Game, CdColors.TealLight, CdColors.TealDark)
    QuickDrop.TOGETHER -> QuickDropLook(CdIcons.Power, CdColors.YellowLight, CdColors.YellowDeep)
    QuickDrop.INBOX -> QuickDropLook(CdIcons.Inbox, CdColors.Gray100, CdColors.Ink2)
    QuickDrop.SCORE -> QuickDropLook(CdIcons.Trophy, CdColors.BlueLight, CdColors.BlueDark)
}

/** 노래's icon colours (canvas `CLR.song`); no other quick drop uses them. */
private val SongTint = Color(0xFFFFF0F5)
private val SongInk = Color(0xFFD6336C)

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun QuickDropEditPreview() {
    CarDropTheme {
        Box(Modifier.fillMaxSize().background(CdColors.Gray100)) {
            QuickDropEditScreen(saved = listOf(QuickDrop.REST, QuickDrop.SHOUT), onCancel = {}, onSave = {})
        }
    }
}
