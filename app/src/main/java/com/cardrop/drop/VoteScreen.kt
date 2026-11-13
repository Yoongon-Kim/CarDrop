package com.cardrop.drop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Car
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.AiTag
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.crew.CrewViewModel
import com.cardrop.drive.PlaceTimes
import com.cardrop.drive.PlaceTrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Board 11 투표 (받는 차) · Owner: Member A.
 * This car picks a place, 아무 데나 좋아 or 난 패스 ([VotePick]), and 응답하기 sends it. When the vote ends, the app
 * moves to board 14 on its own (see DropRouting), except for a car that passed: its route doesn't change, so it
 * stays here, and the button becomes 결과 보기 ([onOpenResult], board 14).
 * [placeTimes] (AppContainer.placeTimes) says how far each place is from this car and how long it takes.
 * [onPass] makes this car skip the stop, as board 14's "우리 차는 안 들르기" does: a pass keeps its route.
 */
@Composable
fun VoteScreen(
    vm: CrewViewModel,
    dropId: String,
    placeTimes: StateFlow<PlaceTimes>,
    onPass: (dropId: String) -> Unit,
    onOpenResult: (dropId: String) -> Unit,
) {
    val drops by vm.drops.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val times by placeTimes.collectAsStateWithLifecycle()
    val drop = drops.firstOrNull { it.id == dropId } ?: return
    val cars = crew?.cars.orEmpty()
    // Only the sender and the cars it was sent to answer this vote.
    val carCount = drop.voterIds(cars.map { it.id }).size

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dropId) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secondsLeft = ((drop.deadlineMillis - now) / 1000).coerceAtLeast(0)
    var pick by remember(dropId) { mutableStateOf(VotePick.from(drop.votes[vm.myCarId], drop.candidates)) }
    // The place this car's vote counts for (place 1 for 아무 데나); null for a pass.
    val counted = pick.countedId(drop.candidates)
    // Answered, or the vote is over: the pick can't change any more.
    val locked = vm.myCarId in drop.votes || drop.isDecided
    // 돌림판 is a small extra: only before answering, and only when there is something to choose between.
    val canSpin = !locked && drop.candidates.size >= 2
    var wheelOpen by remember(dropId) { mutableStateOf(false) }
    // A pass keeps this car's route. A new destination is for the whole crew, so that vote can't be passed.
    val canPass = drop.topic != DropTopic.DESTINATION

    // The sender's car, as the tag names it: "아빠 차의 제안". This car's own vote says "우리 차".
    val sender = if (drop.fromCarId == vm.myCarId) "우리 차" else vm.carName(drop.fromCarId)
    val vote = pick.vote(drop.candidates)

    // The screen, with the wheel's pop-over above it.
    Box(Modifier.fillMaxSize()) {
        // 응답하기 sits at the bottom of the screen (target line 467, `margin-top: auto`): 72 high, corners 22, 20 / 800.
        ScreenColumn(bottom = {
            PrimaryButton(
                text = when {
                    drop.isDecided -> "결과 보기"
                    locked -> "응답했어요"
                    else -> "응답하기"
                },
                enabled = drop.isDecided || (!locked && vote != null),
                onClick = {
                    if (drop.isDecided) {
                        onOpenResult(dropId)
                    } else if (vote != null) {
                        // A pass skips the stop first, so the stop never reaches this car's route, even if this
                        // answer is the last one and ends the vote at once.
                        if (pick == VotePick.Pass) onPass(dropId)
                        vm.vote(dropId, vote)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 72.dp,
                radius = 22.dp,
                fontSize = 20.sp,
            )
        }) {
            // Target lines 426-430 and 711-713: an orange tag, the big title and why these places, at the top.
            ScreenHeader(
                eyebrow = null,
                title = drop.title,
                tag = {
                    Pill(
                        "${sender}의 제안 · ${drop.topic.voteTag()}",
                        background = CdColors.OrangeLight,
                        color = CdColors.OrangeDark,
                        fontSize = 13.sp,
                        padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    )
                },
                titleSize = 32.sp,
                titleLetterSpacing = TightLetterSpacing,
                subtitle = drop.message.ifBlank { null },
                subtitleSize = 15.sp,
                gap = 6.dp,
                verticalAlignment = Alignment.Top,
            ) {
                // The spin chip over the time left, at the right (target lines 432-436, 784). The chip opens the
                // wheel and closes it again, so it stays while the wheel is open.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canSpin) {
                        CdChip(
                            "고민되면 돌려보기",
                            onClick = { wheelOpen = !wheelOpen },
                            style = ChipStyle.Dashed,
                            icon = CdIcons.Wheel,
                            height = 36.dp,
                            radius = 999.dp,
                            fontSize = 12.sp,
                            iconSize = 24.dp,
                            iconTint = Color.Unspecified, // the wheel keeps its three colours
                            contentPadding = PaddingValues(start = 6.dp, end = 12.dp),
                        )
                    }
                    // Once the vote is over (everyone answered early, or time ran out): "마감" in grey.
                    Text(
                        if (drop.isDecided) "마감" else "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                        color = if (drop.isDecided) CdColors.Gray450 else CdColors.Alert,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 38.sp,
                    )
                }
            }
            // The places, 3 to a row and 12 apart; a 4th and 5th place start a new row (target lines 450-456). Cards in
            // a row are as tall as the tallest, like the target's grid.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                drop.candidates.withIndex().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (index, candidate) ->
                            // The cars that voted for it, in crew order; this car too once it picked it (target line 718).
                            val voters = cars.filter { car ->
                                drop.votes[car.id] == candidate.id || (car.id == vm.myCarId && !locked && counted == candidate.id)
                            }
                            CandidateCard(
                                number = index + 1,
                                candidate = candidate,
                                trip = times.trip(candidate.place),
                                voters = voters,
                                picked = pick == VotePick.Place(candidate.id),
                                counted = counted == candidate.id,
                                onClick = if (locked) null else ({ pick = VotePick.Place(candidate.id) }),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            // 아무 데나 좋아 and 난 패스 at the left; what the driver can say and how many cars answered at the right
            // (target lines 460-465, 722). Voting by voice isn't built yet; the hint is the design's.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceButton(
                    "아무 데나 좋아",
                    on = pick == VotePick.Anywhere,
                    onBackground = CdColors.BlueLight,
                    onText = CdColors.BlueDark,
                    onEdge = CdColors.Blue,
                    enabled = !locked,
                ) { pick = VotePick.Anywhere }
                if (canPass) {
                    ChoiceButton(
                        "난 패스, 먼저 갈게",
                        on = pick == VotePick.Pass,
                        onBackground = CdColors.RedLight,
                        onText = CdColors.Red,
                        onEdge = CdColors.Alert,
                        enabled = !locked,
                    ) { pick = VotePick.Pass }
                }
                Spacer(Modifier.weight(1f))
                Text(if (canPass) "“1번”, “아무 데나”, “패스”" else "“1번”, “아무 데나”", color = CdColors.Gray600, fontSize = 14.sp)
                Pill(
                    "${drop.votes.size}/$carCount 응답",
                    background = CdColors.White,
                    color = CdColors.Ink,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            // What this car's answer means, said by the AI: "**{1번}에 1표** · “1번”, “첫 번째”도 같은 표예요" (target
            // lines 466, 724). White, corners 18, padding 14 x 16, the small AI tag 12 to the left of the 15px text.
            val (meaning, aside) = pick.explain(drop.candidates)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CdColors.White)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiTag(small = true)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(meaning) }
                        append(" · ")
                        withStyle(SpanStyle(color = CdColors.Gray600)) { append(aside) }
                    },
                    color = CdColors.Ink,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // The wheel pops over the top right of the screen (target line 440: right 28, top 100, 300 wide), rising
        // 18 px while it fades in for 0.35 s (the target's `cdPop`). Only the spin chip closes it again.
        val rise = with(LocalDensity.current) { 18.dp.roundToPx() }
        AnimatedVisibility(
            visible = canSpin && wheelOpen,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 28.dp),
            enter = fadeIn(tween(350, easing = EaseOut)) + slideInVertically(tween(350, easing = EaseOut)) { rise },
            exit = ExitTransition.None,
        ) {
            VoteRoulette(
                candidates = drop.candidates,
                // Only selects it; the driver still presses 응답하기 to send the vote.
                onPick = { candidateId ->
                    pick = VotePick.Place(candidateId)
                    wheelOpen = false
                },
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

/**
 * 아무 데나 좋아 or 난 패스, 먼저 갈게: 56 high, corners 18, 16 / 800 (target line 722). White when not picked; when
 * picked, [onBackground] with [onText] and a 2 px [onEdge] (blue for 아무 데나, red for 패스).
 */
@Composable
private fun ChoiceButton(
    text: String,
    on: Boolean,
    onBackground: Color,
    onText: Color,
    onEdge: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .height(56.dp)
            .clip(shape)
            .background(if (on) onBackground else CdColors.White)
            .border(2.dp, if (on) onEdge else CdColors.White, shape)
            .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) onText else CdColors.Ink2, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/**
 * One place in the vote (target lines 452-456, 716-721): a number circle and this car's time to get there at the
 * top, the name, a grey line (distance · the AI's reason) and, under a thin line, the cars that voted for it.
 * White, corners 24, padding 18, 8 between the lines. [picked] = this car picked it: light blue with a blue edge.
 * [counted] = this car's vote goes here without picking it (아무 데나 좋아 counts for place 1): a paler blue with a
 * dashed edge. The number circle is filled in both cases. [onClick] = null once this car has answered.
 */
@Composable
private fun CandidateCard(
    number: Int,
    candidate: Candidate,
    trip: PlaceTrip?,
    voters: List<Car>,
    picked: Boolean,
    counted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    picked -> CdColors.BlueLight
                    counted -> CdColors.BlueTint
                    else -> CdColors.White
                },
            )
            .then(
                when {
                    picked -> Modifier.border(2.dp, CdColors.Blue, shape)
                    counted -> Modifier.dashedEdge(CountedEdge, radius = 24.dp)
                    else -> Modifier
                },
            )
            .selectable(selected = picked, enabled = onClick != null, role = Role.RadioButton, onClick = { onClick?.invoke() })
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberCircle(number, filled = picked || counted)
            Spacer(Modifier.weight(1f))
            // Places off the route have no time (PlaceTimes).
            trip?.let { Text(it.minutesText, color = CdColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Text(
            candidate.place.name,
            color = CdColors.Ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(trip?.kmText, candidate.reason.ifBlank { null }).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, color = CdColors.Gray600, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(CdColors.Gray100, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            voters.forEach { CarBadge(it, size = 28) }
            Text(
                if (voters.isEmpty()) "아직 없음" else "${voters.size}표",
                color = CdColors.Gray600,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The place's number: an orange ring, or filled blue once this car's vote goes there (target line 721). */
@Composable
private fun NumberCircle(number: Int, filled: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(CdColors.Blue) else Modifier.border(2.dp, CdColors.Orange, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = if (filled) CdColors.White else CdColors.OrangeDark, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** The dashed edge of place 1 when 아무 데나 좋아 is picked (`2px dashed #90C2FF`, target line 720). */
private val CountedEdge = Color(0xFF90C2FF)

/**
 * A 2 dp dashed line just inside the edge, like CSS `border: 2px dashed`: dashes and gaps 6 dp long (browsers
 * draw a dashed border's dashes about 3 times as long as it is thick). [radius] = the shape's corners.
 */
private fun Modifier.dashedEdge(color: Color, radius: Dp): Modifier = drawBehind {
    val line = 2.dp.toPx()
    val dash = 6.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius((radius.toPx() - line / 2).coerceAtLeast(0f)),
        style = Stroke(width = line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/** What the vote is about, after "{보낸 차}의 제안 · " in the tag (target lines 711-713: "식사", "주유"). */
private fun DropTopic.voteTag(): String = when (this) {
    DropTopic.REST -> "휴게소"
    DropTopic.FOOD -> "식사"
    DropTopic.FUEL -> "주유"
    DropTopic.DESTINATION -> "목적지 변경"
    DropTopic.CAFE -> "카페"
    DropTopic.CUSTOM -> "들를 곳"
    DropTopic.MESSAGE -> "한마디"
}
