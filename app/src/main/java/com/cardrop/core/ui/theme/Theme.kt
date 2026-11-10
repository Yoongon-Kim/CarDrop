package com.cardrop.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.CarColor

/** Colours taken from the CarDrop design boards (design/FinalApp.dc.html). */
object CdColors {
    val Blue = Color(0xFF3182F6)
    val BlueDark = Color(0xFF1B64DA)
    val BlueLight = Color(0xFFE8F3FF)
    val Ink = Color(0xFF191F28)
    val Ink2 = Color(0xFF333D4B)
    val Gray600 = Color(0xFF4E5968)
    val Gray500 = Color(0xFF6B7684)
    val Gray400 = Color(0xFFB0B8C1)
    val Gray200 = Color(0xFFE5E8EB)
    val Gray100 = Color(0xFFF2F4F6)
    val White = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF8A3D)
    val OrangeLight = Color(0xFFFFF4E6)
    val OrangeDark = Color(0xFFB85700)
    val NavBackground = Color(0xFFE9EEF3)
    val NavBanner = Color(0xFF1E2A4A)
    val Purple = Color(0xFF8B5CF6)
    val Teal = Color(0xFF00B8A9)
    val Yellow = Color(0xFFFFB020)
}

fun CarColor.toColor(): Color = when (this) {
    CarColor.BLUE -> CdColors.Blue
    CarColor.PURPLE -> CdColors.Purple
    CarColor.TEAL -> CdColors.Teal
    CarColor.YELLOW -> CdColors.Yellow
    CarColor.GRAY -> CdColors.Gray200
}

private val CdTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, color = CdColors.Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, color = CdColors.Gray600),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdColors.Gray600),
)

@Composable
fun CarDropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CdColors.Blue,
            onPrimary = CdColors.White,
            background = CdColors.Gray100,
            surface = CdColors.White,
            onSurface = CdColors.Ink,
        ),
        typography = CdTypography,
        content = content,
    )
}
