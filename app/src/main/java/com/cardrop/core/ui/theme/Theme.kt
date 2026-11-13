package com.cardrop.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cardrop.R
import com.cardrop.core.model.CarColor

/** Colours taken from the CarDrop design boards (design/FinalApp.dc.html). */
object CdColors {
    val Blue = Color(0xFF3182F6)
    val BlueDark = Color(0xFF1B64DA)
    val BlueLight = Color(0xFFE8F3FF)
    /** Background of a ticked row. */
    val BlueTint = Color(0xFFF4F9FF)
    val Ink = Color(0xFF191F28)
    val Ink2 = Color(0xFF333D4B)
    val Gray600 = Color(0xFF4E5968)
    val Gray500 = Color(0xFF6B7684)
    /** Placeholder text and the text on greyed-out buttons. */
    val Gray450 = Color(0xFF8B95A1)
    val Gray400 = Color(0xFFB0B8C1)
    val Gray300 = Color(0xFFD1D6DB)
    val Gray200 = Color(0xFFE5E8EB)
    val Gray100 = Color(0xFFF2F4F6)
    val Gray50 = Color(0xFFF9FAFB)
    val White = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF8A3D)
    val OrangeLight = Color(0xFFFFF4E6)
    val OrangeDark = Color(0xFFB85700)
    /** Border of board 06's fuel banner. */
    val OrangeLine = Color(0xFFFFC58F)
    /** Social drops (한마디, 음성 메시지). Not the same as [Purple], which is the 삼촌 car's colour. */
    val Violet = Color(0xFF6A4CF0)
    val VioletLight = Color(0xFFF1EDFF)
    val VioletDark = Color(0xFF5B3FD9)
    val VioletInk = Color(0xFF3B2A9E)
    /** Errors and delete buttons. */
    val Red = Color(0xFFD22030)
    val RedLight = Color(0xFFFFEEEE)
    val NavBackground = Color(0xFFE9EEF3)
    val NavBanner = Color(0xFF1E2A4A)
    val Purple = Color(0xFF8B5CF6)
    val Teal = Color(0xFF00B8A9)
    val TealLight = Color(0xFFE6F8F4)
    val TealDark = Color(0xFF0B7F6F)
    val Yellow = Color(0xFFFFB020)
    /** Board 05: background and number circle of the chosen station. */
    val YellowLight = Color(0xFFFFF7DD)
    val Amber = Color(0xFFF0A30A)
    /** Board 04: the "음성 모드" pill and the "… 방면 →" label. */
    val Green = Color(0xFF0B7F5A)
    val GreenLight = Color(0xFFE6F8F1)
    /** Unread counts, e.g. board 04's inbox badge. */
    val Alert = Color(0xFFF04452)
    /** Border of a crew card whose car is low on fuel. */
    val FuelLow = Color(0xFFFFB067)
    /** Icon colours of the 식사 and 주유 quick drops. */
    val OrangeDeep = Color(0xFFC25A00)
    val YellowDeep = Color(0xFF8A5A00)
}

fun CarColor.toColor(): Color = when (this) {
    CarColor.BLUE -> CdColors.Blue
    CarColor.PURPLE -> CdColors.Purple
    CarColor.TEAL -> CdColors.Teal
    CarColor.YELLOW -> CdColors.Yellow
    CarColor.GRAY -> CdColors.Gray200
}

/** The light background of a car's crew card (board 04), from the design. */
fun CarColor.tint(): Color = when (this) {
    CarColor.BLUE -> Color(0xFFEEF5FF)
    CarColor.PURPLE -> Color(0xFFF3EEFF)
    CarColor.TEAL -> CdColors.TealLight
    CarColor.YELLOW -> CdColors.YellowLight
    CarColor.GRAY -> CdColors.Gray100
}

/**
 * The design's `letter-spacing: -0.02em`, which some big titles have (boards 01, 04, 05, 11), e.g.
 * `ScreenHeader(..., titleLetterSpacing = TightLetterSpacing)` or `Text(..., letterSpacing = TightLetterSpacing)`.
 */
val TightLetterSpacing = (-0.02).em

/**
 * Noto Sans KR, the design's font (`font-family: 'Noto Sans KR'`). The app carries it (the Pleos image has
 * no Google Play services, so fonts can't be downloaded): `res/font/noto_sans_kr.ttf` is Google Fonts'
 * `NotoSansKR[wght].ttf`, a variable font, so one file holds every weight from 100 to 900. Each [Font]
 * below asks that file for one weight (`variationSettings`), so `FontWeight.ExtraBold` is the design's 800.
 * Licence: SIL Open Font License 1.1, copied to `assets/licenses/NotoSansKR-OFL.txt`.
 *
 * [CarDropTheme] gives it to every text style, so a `Text` picks it up by itself. Only a `TextStyle(...)`
 * written by hand (e.g. for a `BasicTextField`) needs `fontFamily = NotoSansKr`.
 */
@OptIn(ExperimentalTextApi::class) // Font(..., variationSettings) is marked experimental in Compose 1.7.
val NotoSansKr = FontFamily(
    listOf(
        FontWeight.W100, FontWeight.W200, FontWeight.W300, FontWeight.W400, FontWeight.W500,
        FontWeight.W600, FontWeight.W700, FontWeight.W800, FontWeight.W900,
    ).map { weight ->
        Font(R.font.noto_sans_kr, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))
    },
)

private val CdTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink),
    bodyLarge = TextStyle(fontSize = 16.sp, color = CdColors.Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, color = CdColors.Gray600),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CdColors.Gray600),
).withFontFamily(NotoSansKr)

/**
 * Every one of Material's 15 text styles with [family], also the ones we don't set above: a button's
 * text, for example, uses `labelLarge`.
 */
private fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
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
