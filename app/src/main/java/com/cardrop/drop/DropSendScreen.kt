package com.cardrop.drop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel
import kotlinx.coroutines.launch

/**
 * Board 08 드랍 보내기 · Owner: Member A. The recipients and the decision row's second page are the live canvas's
 * (lead, 2026-11-15; they replace D13's 조합 만들기 and bring back what D11 took off).
 * Choose who gets it (크루 전체, or any cars ticked one by one), then what to send: a decision topic
 * (휴게소, 식사, 주유 · 충전) opens board 10 (AI candidates). The arrow at the row's end (or a swipe) turns it to
 * 카페 (board 10 with the AI's cafés) and 직접 추가 (board 10 without AI picks: the driver adds the places).
 * 일행에게 한마디 opens board 09 (typing or speaking; the target's 음성 메시지 tile is gone), 노래 드랍 board 12 ([onSong]). They all get the chosen cars (empty = whole
 * crew). 게임 초대 opens board 15 ([onGames]), where the driver picks the game and who to play; only in P
 * ([gamesOpen]), greyed out while driving. The ✕ at the top
 * right ([onClose]) goes back to 홈 (board 04).
 *
 * 목적지 변경 is different: a driver who wants another destination knows where, so the tile opens
 * "어디로 제안할까요?" ([DestinationProposalPanel]) on this screen: search the place, confirm, and it goes out as a
 * 찬성 / 반대 proposal ([onProposalSent] gets the new drop). Its "AI 추천 받기" still opens board 10.
 */
@Composable
fun DropSendScreen(
    vm: CrewViewModel,
    onPickTopic: (topic: DropTopic, toCarIds: List<String>) -> Unit,
    onShout: (toCarIds: List<String>) -> Unit,
    onGames: () -> Unit,
    onClose: () -> Unit,
    onProposalSent: (Drop) -> Unit,
    onSong: (toCarIds: List<String>) -> Unit,
    /** False while the car is driving ([com.cardrop.drive.gamesOpen]): 게임 초대 is greyed out and can't be tapped. */
    gamesOpen: Boolean = true,
    /** The driver's own tiles on the decision row's second page ([DropSlotRules]). */
    slots: List<PlaceKind> = emptyList(),
    onAddSlot: (PlaceKind) -> Unit = {},
    onRemoveSlot: (PlaceKind) -> Unit = {},
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val others = crew?.cars.orEmpty().filterNot { it.isMe }
    // The cars ticked one by one. None of them (or all of them) = 크루 전체.
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val toCarIds = Recipients.toCarIds(others, picked)
    // True after 목적지 변경 was tapped: the proposal's search takes this screen's place until it is sent or closed.
    var proposing by remember { mutableStateOf(false) }

    if (proposing) {
        DestinationProposalPanel(
            vm = vm,
            title = "어디로 제안할까요?",
            recipients = crew?.recipientLabel(toCarIds, vm.myCarId) ?: "크루 전체",
            onBack = { proposing = false },
            onAskAi = { onPickTopic(DropTopic.DESTINATION, toCarIds) },
            onSend = { place -> vm.proposeDestination(place, toCarIds, onSent = onProposalSent) },
        )
        return
    }

    ScreenColumn {
        // The title alone, with a ✕ at the right (target lines 364-366).
        ScreenHeader(eyebrow = null, title = "누구에게 보낼까요?", gap = 0.dp, verticalAlignment = Alignment.CenterVertically) {
            CloseButton(onClick = onClose)
        }
        // 크루 전체, then every other car, 4 to a row (live canvas). Any cars can be ticked together; ticking 크루 전체,
        // or the last car left, sends to the whole crew again.
        val wholeCrew = toCarIds.isEmpty()
        (listOf<Car?>(null) + others).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { car ->
                    if (car == null) {
                        RecipientButton("크루 전체", crewMeta(others), wholeCrew, CircleShape, Modifier.weight(1f)) {
                            picked = emptySet()
                        }
                    } else {
                        val on = car.id in toCarIds
                        RecipientButton(car.name, if (on) "선택됨" else "누르면 추가", on, RoundedCornerShape(8.dp), Modifier.weight(1f)) {
                            picked = Recipients.toggle(picked, car.id, others)
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } // keeps the four columns
            }
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
        // Decision tiles 4 to a row (in two pages), social tiles 5, 10 apart (target lines 683-687).
        TopicGroup("결정 드랍 · 음성으로 묻고 투표해요", CdColors.BlueDark, CdColors.BlueLight) {
            DecisionPages(
                onTopic = { topic -> onPickTopic(topic, toCarIds) },
                onDestination = { proposing = true },
                slots = slots,
                onAddSlot = onAddSlot,
                onRemoveSlot = onRemoveSlot,
            )
        }
        TopicGroup("소셜 드랍 · 화면에만 조용히", CdColors.VioletDark, CdColors.VioletLight) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TopicTile("일행에게 한마디", "자주 쓰는 말", CdIcons.Shout, CdColors.VioletLight, CdColors.VioletDark, Modifier.weight(1f)) {
                    onShout(toCarIds)
                }
                // The target's 음성 메시지 tile is gone (Member A, 2026-11-15): it only opened board 09 again, which already
                // has its 말하기 button.
                // 노래 드랍 opens board 12 for the chosen cars. 같이 켜기 (points) is cut: shown as in the target, greyed out (D8).
                TopicTile("노래 드랍", "Apple Music", CdIcons.Music, SongTint, SongInk, Modifier.weight(1f)) {
                    onSong(toCarIds)
                }
                // Only once the car has stopped (P): while driving it is greyed out, like 04's 게임 초대 quick drop, rather
                // than opening board 15's "주행 중에는 게임을 열 수 없어요" (Member A, 2026-11-15).
                TopicTile(
                    "게임 초대", "정차 시 시작", CdIcons.Game, CdColors.TealLight, CdColors.TealDark, Modifier.weight(1f),
                    enabled = gamesOpen, onClick = onGames,
                )
                TopicTile(
                    "같이 켜기", "P 정차 중 · 포인트", CdIcons.Power, CdColors.YellowLight, CdColors.YellowDeep, Modifier.weight(1f),
                    enabled = false, metaColor = CdColors.OrangeDark, metaWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/**
 * The decision row in two pages, as on the live canvas (its `morePlaces`): 휴게소 / 식사 / 주유 · 충전 / 목적지 변경,
 * then 카페 and the driver's own tiles (Member A, 2026-11-15; it was 직접 추가): up to 3 of 졸음쉼터 · 편의점 · 전기차 충전
 * ([slots]) and a dashed "+" while one is left. "+" shows the kinds not picked yet in the page's place, with 취소; a
 * long press on an own tile takes it out. The arrow at the right end (44 wide, as high as the tiles, light blue with corners 16)
 * turns the page, › forward and ‹ back; a swipe on the tiles turns it too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DecisionPages(
    onTopic: (DropTopic) -> Unit,
    onDestination: () -> Unit,
    slots: List<PlaceKind>,
    onAddSlot: (PlaceKind) -> Unit,
    onRemoveSlot: (PlaceKind) -> Unit,
) {
    val pages = rememberPagerState(pageCount = { 2 })
    // True after "+": the second page offers the kinds not picked yet.
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(state = pages, modifier = Modifier.weight(1f), pageSpacing = 10.dp) { page ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (page == 0) {
                    TopicTile("휴게소", "AI 3곳 추천", CdIcons.Rest, CdColors.BlueLight, CdColors.BlueDark, Modifier.weight(1f)) {
                        onTopic(DropTopic.REST)
                    }
                    TopicTile("식사", "AI 음식점 추천", CdIcons.Food, CdColors.OrangeLight, CdColors.OrangeDeep, Modifier.weight(1f)) {
                        onTopic(DropTopic.FOOD)
                    }
                    TopicTile("주유 · 충전", "잔량 기준 추천", CdIcons.Fuel, CdColors.YellowLight, CdColors.YellowDeep, Modifier.weight(1f)) {
                        onTopic(DropTopic.FUEL)
                    }
                    // A new crew destination: the driver searches for it and proposes it (찬성 / 반대).
                    TopicTile("목적지 변경", "직접 골라 제안", CdIcons.Flag, CdColors.TealLight, CdColors.TealDark, Modifier.weight(1f), onClick = onDestination)
                } else if (picking) {
                    // "+": the kinds not picked yet, then 취소.
                    val choices = DropSlotRules.choices(slots)
                    choices.forEach { kind ->
                        val (tint, ink) = kind.colors()
                        TopicTile(kind.label, "눌러서 추가", kind.icon(), tint, ink, Modifier.weight(1f)) {
                            onAddSlot(kind)
                            picking = false
                        }
                    }
                    TopicTile("취소", "그대로 두기", CdIcons.Close, CdColors.Gray100, CdColors.Ink2, Modifier.weight(1f)) {
                        picking = false
                    }
                    repeat(3 - choices.size) { Spacer(Modifier.weight(1f)) }
                } else {
                    // The canvas's second page: 카페 has the 휴게소 cup in the 식사 colours, then the driver's own tiles.
                    TopicTile("카페", "커피 · 드라이브 스루", CdIcons.Rest, CdColors.OrangeLight, CdColors.OrangeDeep, Modifier.weight(1f)) {
                        onTopic(DropTopic.CAFE)
                    }
                    slots.forEach { kind ->
                        val (tint, ink) = kind.colors()
                        TopicTile(
                            kind.label, kind.sub, kind.icon(), tint, ink, Modifier.weight(1f),
                            onLongClick = { onRemoveSlot(kind) },
                        ) { onTopic(kind.topic) }
                    }
                    if (DropSlotRules.showsAdd(slots)) {
                        TopicTile("추가", "장소 종류 고르기", CdIcons.Plus, CdColors.BlueLight, CdColors.BlueDark, Modifier.weight(1f), dashed = true) {
                            picking = true
                        }
                    }
                    repeat(3 - slots.size - (if (DropSlotRules.showsAdd(slots)) 1 else 0)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val more = pages.currentPage == 1
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CdColors.BlueLight)
                .clickable(onClickLabel = if (more) "이전 장소 유형" else "다른 장소 유형", role = Role.Button) {
                    scope.launch { pages.animateScrollToPage(if (more) 0 else 1) }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The canvas draws the text ‹ / ›; the back button's chevron (turned round for ›) reads better on the car screen.
            Icon(
                CdIcons.Back,
                contentDescription = null,
                tint = CdColors.BlueDark,
                modifier = Modifier.size(26.dp).rotate(if (more) 0f else 180f),
            )
        }
    }
}

/** The icon of an own tile on board 08's second page. */
private fun PlaceKind.icon(): ImageVector = when (this) {
    PlaceKind.DROWSY -> CdIcons.Moon
    PlaceKind.STORE -> CdIcons.Bag
    PlaceKind.EV -> CdIcons.Bolt
}

/** Its icon square's colours: 졸음쉼터 violet, 편의점 teal, 전기차 충전 yellow. */
private fun PlaceKind.colors(): Pair<Color, Color> = when (this) {
    PlaceKind.DROWSY -> CdColors.VioletLight to CdColors.VioletDark
    PlaceKind.STORE -> CdColors.TealLight to CdColors.TealDark
    PlaceKind.EV -> CdColors.YellowLight to CdColors.YellowDeep
}

/** 노래 드랍's icon colours (target line 658, `song`); no other screen uses them. */
private val SongTint = Color(0xFFFFF0F5)
private val SongInk = Color(0xFFD6336C)

/**
 * One tile: a 48 px icon square (corners 16, a 24 px icon in the tile's colours), the name (16 / 800) and a grey
 * line (12px), centred, 6 apart; the tile is white, 148 high with corners 22 (target lines 375-381, 658-659, 687).
 * A tile that isn't built ([enabled] = false) is faded to 40%, like a greyed-out chip, and ignores taps.
 */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable, for the own tiles' long press
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
    /** A long press (board 08's own tiles: takes the tile out). */
    onLongClick: (() -> Unit)? = null,
    /** The "+" tile: a light blue edge, so it reads as an empty slot. */
    dashed: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .height(148.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(CdColors.White)
            .then(if (dashed) Modifier.border(BorderStroke(2.dp, CdColors.Blue.copy(alpha = 0.45f)), shape) else Modifier)
            .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
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
 * Who a drop goes to (board 08, as on the live canvas): 크루 전체, or any of the other cars ticked one by one.
 * [picked] = the ticked cars; none of them, or all of them, is the whole crew. Internal (not private) only so
 * RecipientsTest can check it.
 */
internal object Recipients {
    /** The cars to send to, for `Drop.toCarIds`, in crew order; empty = the whole crew. Cars that left are dropped. */
    fun toCarIds(others: List<Car>, picked: Set<String>): List<String> {
        val ids = others.map { it.id }.filter { it in picked }
        return if (ids.size == others.size) emptyList() else ids
    }

    /** Ticks or unticks [carId]. Ticking the last car that wasn't ticked means every car: 크루 전체 (empty) again. */
    fun toggle(picked: Set<String>, carId: String, others: List<Car>): Set<String> {
        val ticked = toCarIds(others, picked).toSet()
        val next = if (carId in ticked) ticked - carId else ticked + carId
        return if (toCarIds(others, next).isEmpty()) emptySet() else next
    }
}

/** 크루 전체's grey line: the other cars without "차", e.g. "삼촌 · 민지 · 이모" (target line 681). */
internal fun crewMeta(others: List<Car>): String =
    if (others.isEmpty()) "아직 혼자예요" else others.joinToString(" · ") { it.name.removeSuffix(" 차") }

/**
 * One recipient button (live canvas): 74 high, corners 20, a 26 px tick box ([boxShape]: round for 크루 전체,
 * corners 8 for a car) 12 left of the name (17 / 800) over a grey line (12px). Ticked = light blue with a blue
 * edge and a blue box with a white tick; otherwise white, with an empty grey box.
 */
@Composable
private fun RecipientButton(name: String, meta: String, selected: Boolean, boxShape: Shape, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .height(74.dp)
            .clip(shape)
            .background(if (selected) CdColors.BlueLight else CdColors.White)
            .border(2.dp, if (selected) CdColors.Blue else CdColors.White, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(boxShape)
                .then(if (selected) Modifier.background(CdColors.Blue) else Modifier.border(2.dp, CdColors.Gray300, boxShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(CdIcons.Check, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(15.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = CdColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = CdColors.Gray600, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
