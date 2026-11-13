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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.NotoSansKr
import com.cardrop.core.ui.theme.TightLetterSpacing

/*
 * Shared building blocks that match the design's look. Use these instead of styling raw
 * Material components on every screen, so all screens stay consistent.
 */

/**
 * The top of every screen: a small grey line (the eyebrow), the big title, an optional grey line under
 * the title (the subtitle) and an optional thing on the right ([trailing]).
 *
 * The defaults are today's look. Set a screen's values from its board, e.g. board 04's header
 * (`<header ... align-items: center>`, eyebrow 14px / 700, `gap: 2px`, title 28px with `letter-spacing: -0.02em`):
 * `ScreenHeader(eyebrow = "…", title = "…", eyebrowWeight = FontWeight.Bold, titleSize = 28.sp,
 * titleLetterSpacing = TightLetterSpacing, gap = 2.dp, verticalAlignment = Alignment.CenterVertically)`.
 */
@Composable
fun ScreenHeader(
    /** The small line above the title; `null` = none (e.g. boards 03 and 09 have only a subtitle). */
    eyebrow: String?,
    title: String,
    modifier: Modifier = Modifier,
    /** Grey by default; the design colours some, e.g. board 05's orange "연료 경고" ([CdColors.OrangeDark]). */
    eyebrowColor: Color = Color.Unspecified,
    eyebrowSize: TextUnit = 14.sp,
    /** Regular (400) by default; the design's 700 is [FontWeight.Bold], 800 is [FontWeight.ExtraBold]. */
    eyebrowWeight: FontWeight = FontWeight.Normal,
    /** Drawn above the title (before the eyebrow, if both are set), e.g. board 11's tag [Pill]. */
    tag: (@Composable () -> Unit)? = null,
    titleSize: TextUnit = 30.sp,
    /** Pass [TightLetterSpacing] where the design's title has `letter-spacing: -0.02em`. */
    titleLetterSpacing: TextUnit = TextUnit.Unspecified,
    /** A grey line under the title (boards 03, 09, 11). */
    subtitle: String? = null,
    subtitleSize: TextUnit = 14.sp,
    /** Space between the tag, eyebrow, title and subtitle (the design's `gap`; none = `0.dp`). */
    gap: Dp = 4.dp,
    /**
     * How the text and [trailing] line up: the design's `align-items`. [Alignment.Bottom] = `flex-end`
     * (today's look), [Alignment.CenterVertically] = `center`, [Alignment.Top] = `flex-start`.
     */
    verticalAlignment: Alignment.Vertical = Alignment.Bottom,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.weight(1f)) {
            tag?.invoke()
            if (eyebrow != null) {
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.bodyMedium,
                    color = eyebrowColor,
                    fontSize = eyebrowSize,
                    fontWeight = eyebrowWeight,
                )
            }
            Text(title, style = MaterialTheme.typography.headlineLarge, fontSize = titleSize, letterSpacing = titleLetterSpacing)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontSize = subtitleSize)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/**
 * White rounded card, the design's main container. The defaults are today's look; set the board's
 * values with [radius] (`border-radius`), [contentPadding] (`padding`) and [gap] (space between items).
 */
@Composable
fun CdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    radius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) CdColors.BlueLight else CdColors.White)
            .then(if (selected) Modifier.border(2.dp, CdColors.Blue, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * The big button. The defaults are today's look; the boards' buttons are 50-80 high, so set the board's
 * [height], [radius] (`border-radius`), [fontSize] / [fontWeight] and [horizontalPadding] per screen.
 * A greyed-out button (`enabled = false`) is light grey with grey text (an app choice: the board draws none).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Blue by default; [CdColors.Violet] on social screens (board 09). */
    containerColor: Color = CdColors.Blue,
    /** White by default; board 14's white button on the blue card uses [CdColors.BlueDark]. */
    contentColor: Color = CdColors.White,
    /** At least this tall (the text is one line, so this is its height). */
    height: Dp = 64.dp,
    radius: Dp = 20.dp,
    fontSize: TextUnit = 19.sp,
    /** [FontWeight.ExtraBold] = the design's 800; [FontWeight.Bold] = 700. */
    fontWeight: FontWeight = FontWeight.ExtraBold,
    horizontalPadding: Dp = 24.dp,
    /** The greyed-out look by default; board 09 keeps its violet, fainter: `CdColors.Violet.copy(alpha = 0.5f)`. */
    disabledContainerColor: Color = DisabledButtonBackground,
    disabledContentColor: Color = DisabledButtonText,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, disabledContainerColor = disabledContainerColor),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = if (enabled) contentColor else disabledContentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The grey button next to a [PrimaryButton]. Same size options as [PrimaryButton], with today's look as defaults. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 64.dp,
    radius: Dp = 20.dp,
    fontSize: TextUnit = 18.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    horizontalPadding: Dp = 24.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CdColors.Gray100,
            contentColor = CdColors.Ink2,
            disabledContainerColor = DisabledButtonBackground,
            disabledContentColor = DisabledButtonText,
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        Text(text, fontSize = fontSize, fontWeight = fontWeight)
    }
}

/*
 * Greyed-out buttons: #E5E8EB with #8B95A1 text, so the text stays readable (Material's default is white
 * text on light grey). The 11-10 boards draw no greyed-out button; these are the newer canvas's colours.
 */
private val DisabledButtonBackground = CdColors.Gray200
private val DisabledButtonText = CdColors.Gray450

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
 * `is-purple` and `is-primary-purple`. [dashedBorder], when set, draws a 1 dp dashed line around the chip.
 */
enum class ChipStyle(val background: Color, val content: Color, val dashedBorder: Color? = null) {
    Gray(CdColors.Gray100, CdColors.Ink2),
    White(CdColors.White, CdColors.Ink2),
    Blue(CdColors.BlueLight, CdColors.BlueDark),
    Violet(CdColors.VioletLight, CdColors.VioletDark),
    Primary(CdColors.Blue, CdColors.White),
    PrimaryViolet(CdColors.Violet, CdColors.White),
    Danger(CdColors.RedLight, CdColors.Red),
    /** White with grey text and a dashed grey line: board 11's "고민되면 돌려보기" (`1px dashed #B0B8C1`). */
    Dashed(CdColors.White, CdColors.Gray600, dashedBorder = CdColors.Gray400),
}

/**
 * Small rounded button (design: `.cd-chip`), e.g. "문구 추가", "삭제", "말해서 추가".
 * The defaults are today's look: 44 high, radius 14, 14sp, a 16 dp icon, 16 dp at each side. Pass
 * `height = 54.dp` to line it up with a [CdTextField], `radius = 999.dp` for a fully round chip.
 * When [enabled] is false it fades to 40% and ignores taps.
 *
 * Board 11's spin chip: `CdChip("고민되면 돌려보기", onClick, style = ChipStyle.Dashed, icon = CdIcons.Wheel,
 * height = 36.dp, radius = 999.dp, fontSize = 12.sp, iconSize = 24.dp, iconTint = Color.Unspecified,
 * contentPadding = PaddingValues(start = 6.dp, end = 12.dp))`.
 */
@Composable
fun CdChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Gray,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    /** The corners (`border-radius`); `999.dp` makes the ends fully round. */
    radius: Dp = 14.dp,
    fontSize: TextUnit = 14.sp,
    /** [FontWeight.ExtraBold] = the design's 800; [FontWeight.Bold] = 700. */
    fontWeight: FontWeight = FontWeight.ExtraBold,
    iconSize: Dp = 16.dp,
    /** The icon's colour; [Color.Unspecified] keeps a coloured icon's own colours (e.g. [CdIcons.Wheel]). */
    iconTint: Color = style.content,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(radius))
            .background(style.background)
            .then(if (style.dashedBorder != null) Modifier.dashedBorder(style.dashedBorder, radius) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
        }
        Text(
            text = text,
            color = style.content,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A 1 dp dashed line just inside the edge, like CSS `border: 1px dashed`: dashes and gaps 3 dp long, as
 * browsers draw a 1 px dashed border. [radius] = the shape's corners (capped at half the height, like CSS).
 */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val line = 1.dp.toPx()
    val dash = 3.dp.toPx()
    val corner = (radius.toPx().coerceAtMost(size.minDimension / 2) - line / 2).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/**
 * One-line text box (design: `.cd-field`). Text longer than [maxLength] is cut off; the design
 * uses 80 for 한마디 and 48 for place names. [focusColor] is the border while typing: blue, or
 * [CdColors.Violet] on social screens. [onDone] runs when the keyboard's done key is pressed.
 * With [onClear], a round ✕ at the right end empties the box (shown only when there is text).
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
    onClear: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = modifier.height(54.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdColors.Ink, fontFamily = NotoSansKr),
        cursorBrush = SolidColor(focusColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onDone()
            defaultKeyboardAction(ImeAction.Done) // hides the keyboard
        }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            val showClear = onClear != null && value.isNotEmpty()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(CdColors.White)
                    .border(2.dp, if (focused) focusColor else CdColors.Gray200, shape)
                    .padding(start = 16.dp, end = if (showClear) 10.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = CdColors.Gray450, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    innerTextField()
                }
                if (showClear) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(CdColors.Gray200)
                            .clickable(role = Role.Button) { onClear?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(CdIcons.Close, contentDescription = "지우기", tint = CdColors.Gray600, modifier = Modifier.size(14.dp))
                    }
                }
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

/**
 * Small fully round label, e.g. "연결됨", "P · 정차 중에만 열려요". The defaults are today's look (14sp,
 * 14 x 8 padding); the boards' pills are mostly 13px with `padding: 6px 12px` ([fontSize], [padding]).
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = CdColors.BlueLight,
    color: Color = CdColors.BlueDark,
    fontSize: TextUnit = 14.sp,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(padding),
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

/**
 * "AI" tag used wherever the AI wrote something. The default is today's round pill (14sp); `small = true` is
 * the small square-ish tag on board 11's AI card (target line 466: 12px / 800, white on #3182F6, radius 6,
 * padding 3 x 7).
 */
@Composable
fun AiTag(small: Boolean = false) {
    if (!small) {
        Pill("AI", background = CdColors.Blue, color = CdColors.White)
        return
    }
    Text(
        text = "AI",
        color = CdColors.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CdColors.Blue)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
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
