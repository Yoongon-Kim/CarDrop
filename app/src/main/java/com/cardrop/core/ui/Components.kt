package com.cardrop.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.ui.theme.CdColors

/*
 * Shared building blocks that match the design's look. Use these instead of styling raw
 * Material components on every screen, so all screens stay consistent.
 */

/** The top of every screen: small grey line, big title, optional thing on the right. */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.bodyMedium)
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** White rounded card, the design's main container. */
@Composable
fun CdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) CdColors.BlueLight else CdColors.White)
            .then(if (selected) Modifier.border(2.dp, CdColors.Blue, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Blue by default; [CdColors.Violet] on social screens (board 09). */
    containerColor: Color = CdColors.Blue,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(
            text,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CdColors.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CdColors.Gray100, contentColor = CdColors.Ink2),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** The square "뒤로" button at the top left of a sub-screen (design: `.cd-back`). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CdColors.White)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CdIcons.Back, contentDescription = "뒤로", tint = CdColors.Ink2, modifier = Modifier.size(24.dp))
    }
}

/**
 * Colours for [CdChip], from the design's `.cd-chip` classes. The design calls the violet ones
 * `is-purple` and `is-primary-purple`.
 */
enum class ChipStyle(val background: Color, val content: Color) {
    Gray(CdColors.Gray100, CdColors.Ink2),
    White(CdColors.White, CdColors.Ink2),
    Blue(CdColors.BlueLight, CdColors.BlueDark),
    Violet(CdColors.VioletLight, CdColors.VioletDark),
    Primary(CdColors.Blue, CdColors.White),
    PrimaryViolet(CdColors.Violet, CdColors.White),
    Danger(CdColors.RedLight, CdColors.Red),
}

/**
 * Small rounded button (design: `.cd-chip`), e.g. "문구 추가", "삭제", "말해서 추가".
 * It is 44 high; pass `Modifier.height(54.dp)` to line it up with a [CdTextField].
 * When [enabled] is false it fades to 40% and ignores taps.
 */
@Composable
fun CdChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Gray,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(style.background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = style.content, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = style.content,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One-line text box (design: `.cd-field`). Text longer than [maxLength] is cut off; the design
 * uses 80 for 한마디 and 48 for place names. [focusColor] is the border while typing: blue, or
 * [CdColors.Violet] on social screens. [onDone] runs when the keyboard's done key is pressed.
 */
@Composable
fun CdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = Int.MAX_VALUE,
    focusColor: Color = CdColors.Blue,
    onDone: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = modifier.height(54.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdColors.Ink),
        cursorBrush = SolidColor(focusColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onDone()
            defaultKeyboardAction(ImeAction.Done) // hides the keyboard
        }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(CdColors.White)
                    .border(2.dp, if (focused) focusColor else CdColors.Gray200, shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = CdColors.Gray450, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}

/** A short status line, e.g. "듣고 있어요…" (design: `.cd-status`). TalkBack reads it out when it changes. */
@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = CdColors.Gray600,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A red message about something to fix, e.g. "이미 있는 문구예요." (design: `.cd-error`). */
@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = CdColors.Red,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Small rounded label, e.g. "연결됨", "P · 정차 중에만 열려요". */
@Composable
fun Pill(text: String, background: Color = CdColors.BlueLight, color: Color = CdColors.BlueDark) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A list row: badge or icon on the left, two lines of text, something on the right. */
@Composable
fun InfoRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        trailing()
    }
}

/** "AI" tag used wherever the AI wrote something. */
@Composable
fun AiTag() {
    Pill("AI", background = CdColors.Blue, color = CdColors.White)
}

/** Placeholder for parts of a screen that are not built yet. Delete it once the part exists. */
@Composable
fun TodoNote(text: String) {
    Text(
        text = "TODO · $text",
        color = CdColors.OrangeDark,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CdColors.OrangeLight)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
