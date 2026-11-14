package com.cardrop.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.ai.AiViewModel.PhraseEditor
import com.cardrop.ai.AiViewModel.ShoutState
import com.cardrop.core.model.Crew
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.core.ui.theme.CdColors

/**
 * Board 09 일행에게 한마디 · Owner: lead. Sent as a SOCIAL drop: on screen only, never spoken.
 * Pick a phrase, then send it to [toCarIds] (the cars chosen on board 08; empty = the whole crew).
 * The driver can add, change and delete phrases; they're kept on the device. [initialText] is what
 * the driver said on 04, if anything: the AI polishes it and it becomes the chosen phrase.
 */
@Composable
fun ShoutScreen(
    vm: AiViewModel,
    initialText: String,
    toCarIds: List<String>,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    LaunchedEffect(initialText) { vm.openShout(initialText) }
    val state by vm.shout.collectAsStateWithLifecycle()
    val crew by vm.crewState.collectAsStateWithLifecycle()
    ShoutContent(
        state = state,
        recipients = shoutRecipients(crew, toCarIds, vm.myCarId),
        exampleCar = vm.exampleCarName(),
        onBack = onBack,
        onSelect = vm::selectPhrase,
        onSelectSpoken = vm::selectSpoken,
        onNew = vm::newPhrase,
        onEdit = vm::editPhrase,
        onChange = vm::changePhrase,
        onCancel = vm::cancelPhrase,
        onSave = vm::savePhrase,
        onDelete = vm::deletePhrase,
        onListen = vm::listenForShout,
        onSend = { vm.sendChosenShout(toCarIds, onSent) },
    )
}

/**
 * Board 09's "받는 차" line: the whole crew with its number of cars, "크루 전체 (3대)" (target 402), and chosen cars
 * by name as everywhere else ("삼촌 차 · 민지 차"). Only 09 shows the count: [Crew.recipientLabel] is shared with
 * boards 08, 10 and the caption after sending. Internal so ShoutRecipientsTest can check it.
 */
internal fun shoutRecipients(crew: Crew?, toCarIds: List<String>, myCarId: String): String {
    val label = crew?.recipientLabel(toCarIds, myCarId) ?: return "크루 전체"
    val others = crew.cars.filterNot { it.id == myCarId }
    val chosen = others.filter { it.id in toCarIds }
    val wholeCrew = chosen.isEmpty() || chosen.size == others.size // the same rule as recipientLabel
    return if (wholeCrew && others.isNotEmpty()) "$label (${others.size}대)" else label
}

/** What board 09 draws for a given [state]. No ViewModel, so the previews below can show it. */
@Composable
private fun ShoutContent(
    state: ShoutState,
    recipients: String,
    exampleCar: String,
    onBack: () -> Unit = {},
    onSelect: (Int) -> Unit = {},
    onSelectSpoken: () -> Unit = {},
    onNew: () -> Unit = {},
    onEdit: () -> Unit = {},
    onChange: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
    onListen: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            Column {
                // 30/800 with normal letter-spacing (target 392).
                Text("일행에게 한마디", color = CdColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("소셜 드랍 · 받는 차에는 음성 없이 화면에만 조용히 떠요", color = CdColors.Gray600, fontSize = 14.sp)
            }
        }

        // The middle scrolls when the list is long; the send row stays at the bottom.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PhraseCard(state, onSelect, onSelectSpoken, onNew, onEdit, onChange, onCancel, onSave, onDelete)
            VoiceRow(exampleCar = exampleCar, enabled = !state.hearing, onListen = onListen)
        }

        // Why the last message wasn't sent (e.g. "출발하면 드랍을 보낼 수 있어요"), right above the send button.
        state.sendError?.let { ErrorText(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("받는 차 · $recipients", color = CdColors.Gray600, fontSize = 15.sp, modifier = Modifier.weight(1f))
            val chosen = state.chosenText
            // The design's send button (402): 68 high, corners 20, sides 34, 18 px, always violet. When it can't send
            // (no phrase, the editor is open, listening) it stays violet, only fainter.
            PrimaryButton(
                text = when {
                    state.hearing -> "듣는 중…"
                    chosen == null -> "문구를 추가해주세요"
                    else -> "“$chosen” 보내기"
                },
                enabled = chosen != null && state.editor == null && !state.hearing,
                onClick = onSend,
                containerColor = CdColors.Violet,
                modifier = Modifier.widthIn(max = 560.dp),
                height = 68.dp,
                radius = 20.dp,
                fontSize = 18.sp,
                horizontalPadding = 34.dp,
                disabledContainerColor = CdColors.Violet.copy(alpha = 0.5f),
                disabledContentColor = CdColors.White,
            )
        }
    }
}

/** The white card: "자주 쓰는 한마디", the editor when open, what was said, and the phrases in 2 columns. */
@Composable
private fun PhraseCard(
    state: ShoutState,
    onSelect: (Int) -> Unit,
    onSelectSpoken: () -> Unit,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CdColors.White).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("자주 쓰는 한마디", color = CdColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            CdChip("문구 추가", onClick = onNew, style = ChipStyle.Violet, icon = CdIcons.Plus)
            CdChip(
                "선택한 문구 고치기",
                onClick = onEdit,
                icon = CdIcons.Pencil,
                enabled = state.selected in state.phrases.indices,
            )
        }
        state.editor?.let { PhraseEditorBox(it, onChange, onCancel, onSave, onDelete) }

        if (state.hearing || state.spoken != null) {
            PhraseButton(
                text = state.spoken?.let { "“$it”" } ?: "듣고 다듬는 중…",
                selected = state.selected == ShoutState.SPOKEN && state.spoken != null,
                onClick = onSelectSpoken,
                icon = CdIcons.Mic,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.phrases.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { column, phrase ->
                        val index = row * 2 + column
                        PhraseButton(
                            text = phrase,
                            selected = index == state.selected,
                            onClick = { onSelect(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One phrase. The chosen one is violet (design: #F1EDFF with a #6A4CF0 border). */
@Composable
private fun PhraseButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    val content = if (selected) CdColors.VioletInk else CdColors.Ink
    Row(
        modifier = modifier
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(if (selected) CdColors.VioletLight else CdColors.Gray50)
            .border(2.dp, if (selected) CdColors.Violet else CdColors.Gray50, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            text,
            color = content,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The editor for a new or changed phrase: up to 80 characters, then 삭제 / 취소 / 저장. */
@Composable
private fun PhraseEditorBox(
    editor: PhraseEditor,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editor.editing) { focus.requestFocus() } // the keyboard opens right away
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CdColors.Gray50)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (editor.editing == null) "새 한마디" else "선택한 한마디 편집",
                color = CdColors.Gray600,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            CdTextField(
                value = editor.text,
                onValueChange = onChange,
                placeholder = "예: 입구에서 만나요",
                maxLength = ShoutPhrases.MAX_LENGTH,
                focusColor = CdColors.Violet,
                onDone = onSave,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editor.error?.let { ErrorText(it) }
            Spacer(Modifier.weight(1f))
            if (editor.editing != null) CdChip("삭제", onClick = onDelete, style = ChipStyle.Danger)
            CdChip("취소", onClick = onCancel, style = ChipStyle.White)
            CdChip("저장", onClick = onSave, style = ChipStyle.PrimaryViolet)
        }
    }
}

/**
 * The violet "직접 말해서 보내기" row. In the design it's only a hint; here a tap also starts listening,
 * because the app has a real microphone now.
 */
@Composable
private fun VoiceRow(exampleCar: String, enabled: Boolean, onListen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CdColors.VioletLight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onListen)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(58.dp).clip(CircleShape).background(CdColors.Violet),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CdIcons.Mic, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("직접 말해서 보내기", color = CdColors.VioletInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            // Sent word for word, no AI polish (Member A, 2026-11-15).
            Text("말한 그대로 글자로 보내요 · “${exampleCar}에 곧 도착해요”", color = CdColors.Gray600, fontSize = 14.sp)
        }
    }
}

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun ShoutPreview() {
    CarDropTheme {
        Box(Modifier.fillMaxSize().background(CdColors.Gray100)) {
            ShoutContent(
                state = ShoutState(phrases = ShoutPhrases.DEFAULTS, selected = 4),
                recipients = "크루 전체 (3대)",
                exampleCar = "삼촌 차",
            )
        }
    }
}

@Preview(widthDp = 979, heightDp = 900)
@Composable
private fun ShoutEditingPreview() {
    CarDropTheme {
        Box(Modifier.fillMaxSize().background(CdColors.Gray100)) {
            ShoutContent(
                state = ShoutState(
                    phrases = ShoutPhrases.DEFAULTS,
                    selected = ShoutState.SPOKEN,
                    spoken = "곧 도착해요!",
                    editor = PhraseEditor(editing = 1, text = "천천히 와요~", error = "이미 있는 문구예요."),
                ),
                recipients = "삼촌 차 · 민지 차",
                exampleCar = "삼촌 차",
            )
        }
    }
}
