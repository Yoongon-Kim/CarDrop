package com.cardrop.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Car
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.toColor

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
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CdColors.Blue),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.White)
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

/** Coloured circle with the car's first letter. TODO(design): swap for the car images in the boards. */
@Composable
fun CarBadge(car: Car, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (car.connected) car.color.toColor() else CdColors.Gray200),
        contentAlignment = Alignment.Center,
    ) {
        Text(car.name.take(1), color = CdColors.White, fontWeight = FontWeight.ExtraBold, fontSize = (size / 2.6).sp)
    }
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
