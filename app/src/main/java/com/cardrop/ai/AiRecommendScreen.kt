package com.cardrop.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.navi.KeyboardRoom
import com.cardrop.navi.PlaceSuggestion
import com.cardrop.navi.PlaceSuggestionList
import com.cardrop.navi.PlaceSuggestions

/**
 * Board 10 AI 추천 (휴게소 · 식사 · 주유) · Owner: lead.
 * The AI's places plus any the driver adds. The ticked ones go into a vote (board 11), sent to
 * [toCarIds], the cars chosen on board 08 (empty = the whole crew).
 */
@Composable
fun AiRecommendScreen(
    vm: AiViewModel,
    topic: DropTopic,
    toCarIds: List<String>,
    onBack: () -> Unit,
    onSent: (Drop) -> Unit,
) {
    LaunchedEffect(topic, toCarIds) { vm.loadCandidates(topic, toCarIds) }
    PlacePicker(
        vm = vm,
        topic = topic,
        toCarIds = toCarIds,
        popup = false,
        onClose = onBack,
        onSent = onSent,
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
    )
}

/**
 * Board 10 as a pop-up over the current screen, for the quick drops on board 04 (design:
 * "퀵 드랍 · 보낼 곳을 골라요"). It always goes to the whole crew. Member B shows it from the drive
 * screen and closes it in [onSent]; the driver stays on 04.
 */
@Composable
fun QuickDropPicker(vm: AiViewModel, topic: DropTopic, onClose: () -> Unit, onSent: (Drop) -> Unit) {
    LaunchedEffect(topic) { vm.loadCandidates(topic) }
    BackHandler(onBack = onClose)
    PopupFrame {
        PlacePicker(
            vm = vm,
            topic = topic,
            toCarIds = emptyList(), // a quick drop always goes to the whole crew
            popup = true,
            onClose = onClose,
            onSent = onSent,
            modifier = Modifier,
        )
    }
}

/** Connects the ViewModel to [PlacePickerContent]. Only the text box's text lives here. */
@Composable
private fun PlacePicker(
    vm: AiViewModel,
    topic: DropTopic,
    toCarIds: List<String>,
    popup: Boolean,
    onClose: () -> Unit,
    onSent: (Drop) -> Unit,
    modifier: Modifier,
) {
    val state by vm.picker.collectAsStateWithLifecycle()
    val suggest by vm.suggestions.collectAsStateWithLifecycle()
    var typed by rememberSaveable(topic) { mutableStateOf("") }
    val recipients = vm.recipientLabel(toCarIds)
    PlacePickerContent(
        title = pickerTitle(topic, state),
        subtitle = if (popup) "퀵 드랍 · 보낼 곳을 골라요 · $recipients" else "${topic.driverQuote()} · 받는 차 $recipients",
        state = state,
        typed = typed,
        popup = popup,
        modifier = modifier,
        suggest = suggest,
        onTypedChange = {
            typed = it
            vm.onTyped(it) // searches after a short pause; the suggestions replace the list
        },
        onClearTyped = {
            typed = ""
            vm.onTyped("")
        },
        onPickSuggestion = {
            vm.pickSuggestion(it)
            typed = ""
        },
        onClose = onClose,
        onToggle = vm::togglePlace,
        onAddTyped = { vm.addTypedPlace(typed) { typed = "" } },
        onListen = vm::listenForPlace,
        onConfirmHeard = vm::confirmHeardPlace,
        onRetry = { vm.loadCandidates(topic, toCarIds) },
        onSend = { vm.sendVote(topic, toCarIds, onSent) },
    )
}

/** What board 10 draws for a given [state]. No ViewModel, so the previews below can show it. */
@Composable
private fun PlacePickerContent(
    title: String,
    subtitle: String,
    state: AiViewModel.PickerState,
    typed: String,
    popup: Boolean,
    modifier: Modifier = Modifier,
    suggest: PlaceSuggestions = PlaceSuggestions(),
    onTypedChange: (String) -> Unit = {},
    onClearTyped: () -> Unit = {},
    onPickSuggestion: (PlaceSuggestion) -> Unit = {},
    onClose: () -> Unit = {},
    onToggle: (String) -> Unit = {},
    onAddTyped: () -> Unit = {},
    onListen: () -> Unit = {},
    onConfirmHeard: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PickerHeader(title, subtitle, popup, onClose)

        // While the driver types, the screen works like Kakao Map's search: the list steps aside, the
        // text box moves up under the title and the places that fit show below it. Pleos's keyboard
        // covers the lower third of the screen without shrinking our window, so the box must not
        // sit low while typing.
        val suggesting = suggest.shown

        // The list scrolls on its own when it is long; the controls below stay in view.
        if (!suggesting) Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("AI가 고르는 중…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.aiError?.let { message ->
                Text(message, color = CdColors.OrangeDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                SecondaryButton("다시 시도", onClick = onRetry)
            }
            state.pick.candidates.forEachIndexed { index, candidate ->
                PlaceRow(
                    candidate = candidate,
                    rank = index,
                    aiPick = !state.pick.isAdded(candidate.id),
                    selected = state.pick.isSelected(candidate.id),
                    popup = popup,
                    onToggle = { onToggle(candidate.id) },
                )
            }
        }

        if (!state.loading) {
            // Always composed here, whatever the mode, so the text box keeps focus and the keyboard stays up.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CdChip(
                    "말해서 추가",
                    onClick = onListen,
                    style = ChipStyle.Blue,
                    icon = CdIcons.Mic,
                    modifier = Modifier.height(54.dp),
                )
                CdTextField(
                    value = typed,
                    onValueChange = onTypedChange,
                    placeholder = "다른 곳을 직접 입력",
                    maxLength = 48,
                    modifier = Modifier.weight(1f),
                    onDone = onAddTyped,
                    onClear = onClearTyped,
                )
                CdChip(
                    "추가",
                    onClick = onAddTyped,
                    style = if (popup) ChipStyle.Gray else ChipStyle.White,
                    modifier = Modifier.height(54.dp),
                )
            }
            state.status?.let { StatusText(it) }
            state.inputError?.let { ErrorText(it) }
            state.heard?.let { place ->
                CdChip("‘${place.name}’ 후보에 넣기", onClick = onConfirmHeard, style = ChipStyle.Blue)
            }
            if (suggesting) {
                val scroll = rememberScrollState()
                LaunchedEffect(suggest.query) { scroll.scrollTo(0) }
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlaceSuggestionList(
                        suggestions = suggest,
                        onPick = onPickSuggestion,
                        bordered = popup,
                        inList = { state.pick.find(it) != null },
                    )
                    // Room to scroll the last places up above the keyboard.
                    Spacer(Modifier.height(KeyboardRoom))
                }
                return@Column // the send button comes back once a place is picked or the box is emptied
            }
            val count = state.pick.selected.size
            PrimaryButton(
                text = if (count > 0) "이 ${count}곳으로 투표 보내기" else "후보를 골라주세요",
                enabled = count > 0,
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().height(if (popup) 64.dp else 76.dp),
            )
        }
    }
}

/** Back button (page) or close button (pop-up), the line about who gets it, and the big title. */
@Composable
private fun PickerHeader(title: String, subtitle: String, popup: Boolean, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!popup) BackButton(onClick = onClose)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(subtitle, fontSize = 14.sp, color = CdColors.Gray600)
            Text(
                title,
                fontSize = if (popup) 24.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                color = CdColors.Ink,
            )
        }
        if (popup) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CdColors.Gray100)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Close, contentDescription = "닫기", tint = CdColors.Ink2, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Tile colours for the AI's 1st, 2nd and 3rd place (design: blue, orange, teal). */
private val tileColors = listOf(
    CdColors.BlueLight to CdColors.BlueDark,
    CdColors.OrangeLight to CdColors.OrangeDeep,
    CdColors.TealLight to CdColors.TealDark,
)

/** One place: tap anywhere on it to tick or untick it. */
@Composable
private fun PlaceRow(
    candidate: Candidate,
    rank: Int,
    aiPick: Boolean,
    selected: Boolean,
    popup: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val borderColor = when {
        selected -> CdColors.Blue
        popup -> CdColors.Gray100
        else -> CdColors.White
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) CdColors.BlueTint else CdColors.White)
            .border(2.dp, borderColor, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = if (popup) 16.dp else 20.dp, vertical = if (popup) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val (tileBackground, tileText) = if (aiPick) tileColors[rank % tileColors.size] else CdColors.Gray100 to CdColors.Ink2
        Box(
            modifier = Modifier
                .size(if (popup) 48.dp else 58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(tileBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                candidate.place.name.take(1),
                color = tileText,
                fontSize = if (popup) 19.sp else 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    candidate.place.name,
                    color = CdColors.Ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (aiPick && rank == 0) AiPickBadge()
            }
            // A place the driver added: "직접 추가한 장소 · <its address>", so two 스타벅스 can be told apart.
            val meta = if (aiPick) candidate.place.detail else joinNonBlank(candidate.reason, candidate.place.detail)
            if (meta.isNotBlank()) {
                Text(meta, color = CdColors.Gray600, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (aiPick && !popup && candidate.reason.isNotBlank()) {
                Text(candidate.reason, color = CdColors.BlueDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Places the driver added have no detour time yet.
        if (aiPick) {
            Text("+${candidate.detourMinutes}분", color = CdColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        CheckCircle(selected)
    }
}

private fun joinNonBlank(vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(" · ")

/** The small "AI 추천" label on the AI's best place. */
@Composable
private fun AiPickBadge() {
    Text(
        "AI 추천",
        color = CdColors.BlueDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CdColors.BlueLight)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Round tick on the right of a row: filled blue when ticked, an empty ring when not. */
@Composable
private fun CheckCircle(on: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (on) Modifier.background(CdColors.Blue) else Modifier.border(2.dp, CdColors.Gray300, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(CdIcons.Check, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(18.dp))
    }
}

/** The dimmed backdrop and white card of a pop-up (design: 620 wide, at most 86% of the height). */
@Composable
private fun PopupFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CdColors.Ink.copy(alpha = 0.32f))
            .pointerInput(Unit) { detectTapGestures() }, // taps on the dimmed part don't reach the screen below
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = maxHeight * 0.86f)
                .shadow(20.dp, shape)
                .background(CdColors.White, shape)
                .padding(26.dp),
        ) {
            content()
        }
    }
}

private fun pickerTitle(topic: DropTopic, state: AiViewModel.PickerState): String {
    val noun = topic.placeNoun()
    return when {
        state.loading || state.aiError != null -> "$noun 추천"
        state.pick.aiCount == 0 -> "어디에 들를까요?"
        else -> "AI가 $noun ${state.pick.aiCount}곳을 골랐어요"
    }
}

private fun DropTopic.placeNoun(): String = when (this) {
    DropTopic.REST -> "휴게소"
    DropTopic.FOOD -> "음식점"
    DropTopic.FUEL -> "주유소"
    DropTopic.DESTINATION -> "목적지"
    DropTopic.CAFE -> "카페"
    DropTopic.MESSAGE, DropTopic.CUSTOM -> "장소"
}

/** What the driver said to start this drop, shown above the title. */
private fun DropTopic.driverQuote(): String = when (this) {
    DropTopic.REST -> "“휴게소 들렀다 가자”"
    DropTopic.FOOD -> "“배고픈데 뭐 먹고 가자”"
    DropTopic.FUEL -> "“주유하고 가자”"
    DropTopic.CAFE -> "“커피 한잔하고 가자”"
    DropTopic.DESTINATION -> "“목적지 바꾸자”"
    DropTopic.CUSTOM -> "직접 제안"
    DropTopic.MESSAGE -> "“여기 들르자”"
}

/** Sample state for the previews: the AI's 3 stops, one unticked, plus one the driver added. */
private val previewState = AiViewModel.PickerState(
    pick = PlacePick.fromAi(
        DemoData.restStops.take(3).mapIndexed { i, place ->
            Candidate(place.id, place, listOf("4대 모두 지나가는 길", "도착 시간 차이 5분", "넓은 주차장")[i], 2 + i * 3)
        },
    ).toggle("munmak").add(DemoData.restStops[4]),
    status = "장소 이름을 확인하고 추가하세요.",
    heard = DemoData.restStops[3],
)

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun PlacePickerPagePreview() {
    CarDropTheme {
        PlacePickerContent(
            title = "AI가 휴게소 3곳을 골랐어요",
            subtitle = "“휴게소 들렀다 가자” · 받는 차 크루 전체",
            state = previewState,
            typed = "",
            popup = false,
            modifier = Modifier.fillMaxSize().background(CdColors.Gray100).padding(horizontal = 28.dp, vertical = 24.dp),
        )
    }
}

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun PlaceSuggestionsPreview() {
    val stop = DemoData.restStops[0]
    CarDropTheme {
        PlacePickerContent(
            title = "AI가 휴게소 3곳을 골랐어요",
            subtitle = "“휴게소 들렀다 가자” · 받는 차 크루 전체",
            state = previewState.copy(status = null, heard = null),
            suggest = PlaceSuggestions(
                    query = "덕평",
                    places = listOf(
                        PlaceSuggestion(stop.copy(id = "kakao-1", name = "덕평자연휴게소 양방향"), "고속도로휴게소", "경기 이천시 마장면 덕이로154번길 287-76", 0.9),
                        PlaceSuggestion(stop.copy(id = "kakao-2", name = "덕평휴게소주유소 양방향"), "SK주유소", "경기 이천시 마장면 덕이로154번길 287-74", 0.75),
                        PlaceSuggestion(stop.copy(id = "kakao-3", name = "덕평IC"), "고속도로IC", "경기 이천시 마장면 덕평리", 1.3),
                    ),
            ),
            typed = "덕평",
            popup = false,
            modifier = Modifier.fillMaxSize().background(CdColors.Gray100).padding(horizontal = 28.dp, vertical = 24.dp),
        )
    }
}

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun QuickDropPickerPreview() {
    CarDropTheme {
        Box(modifier = Modifier.fillMaxSize().background(CdColors.Gray100)) {
            PopupFrame {
                PlacePickerContent(
                    title = "AI가 휴게소 3곳을 골랐어요",
                    subtitle = "퀵 드랍 · 보낼 곳을 골라요 · 크루 전체",
                    state = previewState.copy(status = null, heard = null),
                    typed = "",
                    popup = true,
                )
            }
        }
    }
}
