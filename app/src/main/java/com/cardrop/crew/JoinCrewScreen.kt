package com.cardrop.crew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Place
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.NotoSansKr
import com.cardrop.navi.tripKmText

/** Board 03 코드로 참여 · Owner: Member A. With the fake, code 482917 always works. */
@Composable
fun JoinCrewScreen(
    vm: CrewViewModel,
    /** Where this car's navigator is heading now, or null without a route. Read when the code is complete. */
    routeDestination: () -> Place?,
    onBack: () -> Unit,
    /** The driver picked how to go: [stop] = "…들렀다 가기" (that place becomes the waypoint), null = "바로 …". */
    onJoined: (stop: Place?) -> Unit,
) {
    // rememberSaveable: the digits stay when Android rebuilds the screen (e.g. day / night theme).
    var code by rememberSaveable { mutableStateOf("") }
    val join by vm.join.collectAsStateWithLifecycle()
    val voice by vm.codeVoice.collectAsStateWithLifecycle()
    // Opened with empty boxes: forget a crew found earlier, so the right half matches the boxes.
    LaunchedEffect(Unit) { if (code.isEmpty()) vm.clearJoin() }
    DisposableEffect(Unit) { onDispose { vm.stopListening() } } // leaving the screen stops the mic
    // Every change of the code goes through here: the crew is looked up at the 6th digit.
    val changeCode: (String) -> Unit = { digits ->
        code = digits
        if (digits.length == JoinRules.CODE_LENGTH) vm.joinCrew(digits, routeDestination()) else vm.clearJoin()
    }

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
        // Design lines 181-197: the code on the left half, the crew found with it on the right half,
        // 16 apart. weight(1f) makes the two cards fill the rest of the screen's height (the design's flex: 1).
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CdCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(22.dp),
                gap = 16.dp,
            ) {
                DigitBoxes(code)
                // "듣고 있어요…", or why what was heard didn't fill the boxes (an app extra).
                voice.status?.let { StatusText(it, Modifier.align(Alignment.CenterHorizontally)) }
                Keypad(
                    onDigit = { if (code.length < JoinRules.CODE_LENGTH) changeCode(code + it) },
                    onErase = { if (code.isNotEmpty()) changeCode(code.dropLast(1)) },
                    listening = voice.listening,
                    // 말로: the 6 digits heard go into the boxes, so the crew is looked up at once.
                    onListen = if (vm.canListen) {
                        { vm.listenForCode(onHeard = changeCode) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f), // the keypad takes the rest of the card
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CrewFound(vm, join, onJoined)
            }
        }
    }
}

/**
 * The six boxes of the code (design line 183): 60 x 76, 8 apart, centred, digits 34/800. A box with a
 * digit is blue on light blue, as drawn; the next one to fill has the blue edge too, the rest a grey one.
 */
@Composable
private fun DigitBoxes(code: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(JoinRules.CODE_LENGTH) { i ->
            val digit = code.getOrNull(i)
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 76.dp)
                    .clip(shape)
                    .background(if (digit != null) CdColors.BlueTint else CdColors.White)
                    .border(2.dp, if (i <= code.length) CdColors.Blue else CdColors.Gray200, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit?.toString().orEmpty(), color = CdColors.Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** The keys in their rows (design line 772). */
private val keypadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(VOICE_KEY, "0", ERASE_KEY),
)
private const val VOICE_KEY = "말로"
private const val ERASE_KEY = "지우기"

/**
 * The 3 x 4 keypad under the boxes (design line 184): 1-9, 말로, 0, 지우기; light grey keys 8 apart,
 * radius 16, 24/800. It fills the height it gets. 말로 is greyed out while listening, or when there is
 * no voice service ([onListen] = null).
 */
@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    listening: Boolean,
    onListen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keypadRows.forEach { keys ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    val keyModifier = Modifier.weight(1f).fillMaxHeight()
                    when (key) {
                        VOICE_KEY -> Key(
                            if (listening) "듣는 중…" else VOICE_KEY,
                            onClick = { onListen?.invoke() },
                            enabled = onListen != null && !listening,
                            modifier = keyModifier,
                        )
                        ERASE_KEY -> Key(ERASE_KEY, onClick = onErase, modifier = keyModifier)
                        else -> Key(key, onClick = { onDigit(key.single()) }, modifier = keyModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(CdColors.Gray100)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = CdColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** The right half of board 03: the crew found with the code, or what is happening before that. */
@Composable
private fun CrewFound(vm: CrewViewModel, join: CrewViewModel.JoinState, onJoined: (stop: Place?) -> Unit) {
    when (join) {
        // The board draws only the found crew; before that, one quiet line in the middle (app extra).
        CrewViewModel.JoinState.Idle -> RightHalfNote { Text("코드 6자리를 넣으면 여기에 크루가 보여요", style = NoteStyle) }
        CrewViewModel.JoinState.Searching -> RightHalfNote { Text("크루를 찾는 중…", style = NoteStyle) }
        CrewViewModel.JoinState.NotFound -> RightHalfNote {
            ErrorText("그 코드의 크루가 없어요")
            Text("코드를 확인하고 지우기로 고쳐 주세요", style = NoteStyle)
        }
        // The crew left without this car: it didn't join within the 3 minutes (JoinRules.JOIN_WINDOW_MILLIS).
        CrewViewModel.JoinState.Closed -> RightHalfNote {
            ErrorText("이미 출발한 크루예요")
            Text("3분 안에 참여하지 못했어요", style = NoteStyle)
            Text("방장에게 크루를 다시 만들어 달라고 해 주세요", style = NoteStyle)
        }
        is CrewViewModel.JoinState.Found -> FoundCrewCard(vm, join, onJoined)
    }
}

/**
 * One of board 03's choices (design lines 193-194): full width, text at the left, padding 18, radius 16.
 * [primary] = blue with white text, at least 62 high, 17/800; else light blue with blue text, at least
 * 58 high, 16/800. [suffix] follows the text in small letters (13/500): "· 서울 신촌역을 경유지로".
 */
@Composable
private fun ChoiceButton(text: String, primary: Boolean, onClick: () -> Unit, suffix: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (primary) 62.dp else 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) CdColors.Blue else CdColors.BlueLight)
            .clickable(role = Role.Button, onClick = onClick)
            // A long place name wraps to a second line; 10 above and below keeps it off the edges.
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            buildAnnotatedString {
                append(text)
                if (suffix != null) withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) { append(" $suffix") }
            },
            color = if (primary) CdColors.White else CdColors.BlueDark,
            fontSize = if (primary) 17.sp else 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Centred in the right half, on the grey page. */
@Composable
private fun RightHalfNote(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val NoteStyle = TextStyle(color = CdColors.Gray600, fontSize = 15.sp, fontFamily = NotoSansKr)

/**
 * The crew found with the code (design lines 186-196): a white card, padding 24, 12 between its lines,
 * that pops in like the design's `cdPop` (line 21): it rises 18 px into place while it fades in, 0.5 s.
 */
@Composable
private fun FoundCrewCard(vm: CrewViewModel, join: CrewViewModel.JoinState.Found, onJoined: (stop: Place?) -> Unit) {
    val live by vm.crew.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    // The crew as it is now (more cars can connect while the card is open); until then, the one found.
    val crew = live?.takeIf { it.code == join.crew.code } ?: join.crew
    val pop = remember(join.crew.code) { Animatable(0f) } // 0 = not shown yet, 1 = in place
    LaunchedEffect(join.crew.code) { pop.animateTo(1f, tween(durationMillis = 500, easing = EaseOut)) }
    CdCard(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = pop.value
            translationY = (1f - pop.value) * 18.dp.toPx()
        },
        contentPadding = PaddingValues(24.dp),
        gap = 12.dp,
    ) {
        Pill("크루를 찾았어요", fontSize = 13.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
        Text(crew.name, style = MaterialTheme.typography.headlineLarge)
        // "아빠 차 크루 · 3대 참여 중 · 강릉 경포해변 230km": the host, the other cars already in (not this one,
        // which is only joining), and the road distance once the navigator has the route (as on board 01).
        val carsIn = crew.cars.count { it.connected && it.id != vm.myCarId }
        val distance = trip?.let { " ${tripKmText(it.km)}" }.orEmpty()
        Text(
            "${vm.carName(crew.hostCarId)} 크루 · ${carsIn}대 참여 중 · ${crew.destination.name}$distance",
            color = CdColors.Gray600,
            fontSize = 15.sp,
        )
        // withRo() picks 로 or 으로 for the name: "강릉 경포해변으로", "속초 해수욕장으로", "서울로".
        val toDestination = crew.destination.name.withRo()
        // Where this car was driving before it joined, e.g. 신촌역. Null without a route, or when that was the
        // crew's destination already: then neither the line nor the button shows.
        val stop = JoinRules.stopOnTheWay(join.routeBefore, crew.destination)
        // The decision (design lines 190-194): a light grey box, radius 18, padding 16, 10 between its lines,
        // 6 px further down than the card's other lines.
        Column(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CdColors.Gray50)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$toDestination 안내할까요?", color = CdColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (stop != null) {
                Text("지금 내 내비는 ${stop.name.withRo()} 안내 중이에요", color = CdColors.Gray600, fontSize = 14.sp)
                ChoiceButton(
                    "${stop.name} 들렀다 가기",
                    suffix = "· ${JoinRules.stopAsWaypoint(stop)}",
                    primary = true,
                    onClick = { onJoined(stop) },
                )
            }
            // Light blue next to the stop, as drawn; the blue main button when it is the only way to go.
            ChoiceButton("바로 $toDestination", primary = stop == null, onClick = { onJoined(null) })
        }
        // The footer sits at the bottom of the card (the design's margin-top: auto): the Spacer takes the room left.
        Spacer(Modifier.weight(1f))
        Text("참여하면 도착할 때까지 일행에게 내 위치가 보여요 · 방장이 수락하면 연결돼요", color = CdColors.Gray600, fontSize = 13.sp)
    }
}
