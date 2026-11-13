package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
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
 * Choose who gets it (크루 전체, one car alone, or a 조합 of cars), then what to send: a decision topic
 * (휴게소, 식사, 주유 · 충전, 목적지 변경) opens board 10 (AI candidates), 한마디 and 음성 메시지 open board 09.
 * Both get the chosen cars (empty = whole crew). 게임 초대 opens board 15 ([onGames]), where the driver picks
 * the game and who to play. The ✕ at the top right ([onClose]) goes back to 홈 (board 04).
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
    var sendTo by remember { mutableStateOf<SendTo>(SendTo.Crew) }
    // The cars ticked under 조합 만들기. Kept while another button is picked, so the combination comes back.
    var mixIds by remember { mutableStateOf(emptySet<String>()) }
    val toCarIds = sendTo.toCarIds(others, mixIds)

    ScreenColumn {
        // The title alone, with a ✕ at the right (target lines 364-366).
        ScreenHeader(eyebrow = null, title = "누구에게 보낼까요?", gap = 0.dp, verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClick = onClose)
        }
        // Four buttons, one of them picked (D13, target lines 368-369 and 681): 크루 전체, the first two other
        // cars alone, and 조합 만들기 (the target doesn't draw what it opens: the app shows the per-car cards).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val singles = others.take(2)
            RecipientButton("크루 전체", crewMeta(others), sendTo == SendTo.Crew, Modifier.weight(1f)) { sendTo = SendTo.Crew }
            singles.forEach { car ->
                val one = SendTo.OneCar(car.id)
                RecipientButton("${car.name}만", "1대", sendTo == one, Modifier.weight(1f)) { sendTo = one }
            }
            repeat(2 - singles.size) { Spacer(Modifier.weight(1f)) } // keeps the four columns
            RecipientButton("조합 만들기", "차를 골라 묶기", sendTo == SendTo.Combination, Modifier.weight(1f)) {
                sendTo = SendTo.Combination
            }
        }
        if (sendTo == SendTo.Combination) {
            // Any cars, ticked one by one (today's cards). None or all of them = the whole crew.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                others.forEach { car ->
                    CdCard(
                        modifier = Modifier.weight(1f),
                        selected = car.id in mixIds,
                        onClick = { mixIds = if (car.id in mixIds) mixIds - car.id else mixIds + car.id },
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
        }

        // The heading with its hint at the right, on the same baseline, 6 px further down (target line 371).
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "무엇을 보낼까요?",
                color = CdColors.Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alignByBaseline(),
            )
            Text("누르면 바로 다음 단계로 넘어가요", color = CdColors.Gray600, fontSize = 14.sp, modifier = Modifier.alignByBaseline())
        }
        // Decision tiles 4 to a row, social tiles 5, 10 apart (target lines 683-687).
        TopicGroup("결정 드랍 · 음성으로 묻고 투표해요", CdColors.BlueDark, CdColors.BlueLight) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TopicTile("휴게소", "AI 3곳 추천", CdIcons.Rest, CdColors.BlueLight, CdColors.BlueDark, Modifier.weight(1f)) {
                    onPickTopic(DropTopic.REST, toCarIds)
                }
                TopicTile("식사", "AI 음식점 추천", CdIcons.Food, CdColors.OrangeLight, CdColors.OrangeDeep, Modifier.weight(1f)) {
                    onPickTopic(DropTopic.FOOD, toCarIds)
                }
                TopicTile("주유 · 충전", "잔량 기준 추천", CdIcons.Fuel, CdColors.YellowLight, CdColors.YellowDeep, Modifier.weight(1f)) {
                    onPickTopic(DropTopic.FUEL, toCarIds)
                }
                // A vote on a new crew destination, through board 10 like the others (D8).
                TopicTile("목적지 변경", "모두 함께", CdIcons.Flag, CdColors.TealLight, CdColors.TealDark, Modifier.weight(1f)) {
                    onPickTopic(DropTopic.DESTINATION, toCarIds)
                }
            }
        }
        TopicGroup("소셜 드랍 · 화면에만 조용히", CdColors.VioletDark, CdColors.VioletLight) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TopicTile("일행에게 한마디", "자주 쓰는 말", CdIcons.Shout, CdColors.VioletLight, CdColors.VioletDark, Modifier.weight(1f)) {
                    onShout(toCarIds)
                }
                // The target's 음성 메시지 opens board 09 too (`voice: G.shout`, line 660); nothing is recorded.
                TopicTile("음성 메시지", "녹음해서", CdIcons.Mic, CdColors.VioletLight, CdColors.VioletDark, Modifier.weight(1f)) {
                    onShout(toCarIds)
                }
                // Song drop and 같이 켜기 (points) are cut for now: shown as in the target, greyed out (D8).
                TopicTile("노래 드랍", "Apple Music", CdIcons.Music, SongTint, SongInk, Modifier.weight(1f), enabled = false)
                TopicTile("게임 초대", "정차 시 시작", CdIcons.Game, CdColors.TealLight, CdColors.TealDark, Modifier.weight(1f), onClick = onGames)
                TopicTile(
                    "같이 켜기", "P 정차 중 · 포인트", CdIcons.Power, CdColors.YellowLight, CdColors.YellowDeep, Modifier.weight(1f),
                    enabled = false, metaColor = CdColors.OrangeDark, metaWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** 노래 드랍's icon colours (target line 658, `song`); no other screen uses them. */
private val SongTint = Color(0xFFFFF0F5)
private val SongInk = Color(0xFFD6336C)

/**
 * One tile: a 48 px icon square (corners 16, a 24 px icon in the tile's colours), the name (16 / 800) and a grey
 * line (12px), centred, 6 apart; the tile is white, 148 high with corners 22 (target lines 375-381, 658-659, 687).
 * A tile that isn't built ([enabled] = false) is faded to 40%, like a greyed-out chip, and ignores taps.
 */
@Composable
private fun TopicTile(
    name: String,
    meta: String,
    icon: ImageVector,
    iconBackground: Color,
    iconColor: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    /** 같이 켜기's line is orange 800 in the target ("P 정차 중 · 포인트"). */
    metaColor: Color = CdColors.Gray600,
    metaWeight: FontWeight = FontWeight.Normal,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .height(148.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(CdColors.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Text(name, color = CdColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, color = metaColor, fontSize = 12.sp, fontWeight = metaWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A group of tiles under its tag: "결정 드랍 · …" in blue or "소셜 드랍 · …" in violet, as a small pill
 * (13 / 800, corners 8, padding 5 x 10), 8 px above the tiles (target lines 373-374, 683-686).
 */
@Composable
private fun TopicGroup(tag: String, color: Color, background: Color, tiles: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tag,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        tiles()
    }
}

/**
 * Who a drop goes to (board 08, D13): the whole crew, one car alone ("삼촌 차만"), or a 조합 (combination) of
 * cars ticked one by one.
 */
internal sealed interface SendTo {
    data object Crew : SendTo
    data class OneCar(val carId: String) : SendTo
    data object Combination : SendTo
}

/**
 * The cars to send to, for `Drop.toCarIds`, in crew order; empty = the whole crew. [others] = the crew's other
 * cars, [mixIds] = the cars ticked under 조합 만들기: none or all of them is the whole crew, as before.
 * Internal (not private) only so SendToTest can check it.
 */
internal fun SendTo.toCarIds(others: List<Car>, mixIds: Set<String>): List<String> {
    val ids = others.map { it.id }
    return when (this) {
        SendTo.Crew -> emptyList()
        is SendTo.OneCar -> ids.filter { it == carId } // a car that left the crew: the whole crew
        SendTo.Combination -> ids.filter { it in mixIds }.takeIf { it.size < ids.size }.orEmpty()
    }
}

/** 크루 전체's grey line: the other cars without "차", e.g. "삼촌 · 민지 · 이모" (target line 681). */
internal fun crewMeta(others: List<Car>): String =
    if (others.isEmpty()) "아직 혼자예요" else others.joinToString(" · ") { it.name.removeSuffix(" 차") }

/**
 * One of the four recipient buttons: 74 high, corners 20, the name (17 / 800) over a grey line (12px), left-aligned.
 * The picked one is light blue with a blue edge, the others white (target line 681).
 */
@Composable
private fun RecipientButton(name: String, meta: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .height(74.dp)
            .clip(shape)
            .background(if (selected) CdColors.BlueLight else CdColors.White)
            .border(2.dp, if (selected) CdColors.Blue else CdColors.White, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(name, color = CdColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, color = CdColors.Gray600, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
