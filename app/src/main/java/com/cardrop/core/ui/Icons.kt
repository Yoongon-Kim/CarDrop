package com.cardrop.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.cardrop.core.ui.theme.CdColors

/**
 * The line icons drawn in the design boards, copied from their SVG paths (24 x 24, round ends).
 * Show one with `Icon(CdIcons.Mic, contentDescription = "...", tint = ...)`; `tint` sets the colour.
 */
object CdIcons {
    val Back = lineIcon("Back", strokeWidth = 2.4f, "m15 18-6-6 6-6")
    val Close = lineIcon("Close", strokeWidth = 2.4f, "M18 6 6 18M6 6l12 12")
    val Plus = lineIcon("Plus", strokeWidth = 2.6f, "M12 5v14M5 12h14")
    val Pencil = lineIcon("Pencil", strokeWidth = 2.2f, "m4 16-1 5 5-1L20 8l-4-4zM14 6l4 4")
    val Mic = lineIcon(
        "Mic",
        strokeWidth = 2.2f,
        "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z",
        "M19 11a7 7 0 0 1-14 0",
        "M12 18v3",
    )
    val Check = lineIcon("Check", strokeWidth = 3f, "M20 6 9 17l-5-5")
    /** A map pin (board 10's place suggestions). */
    val Pin = lineIcon(
        "Pin",
        strokeWidth = 2.2f,
        "M12 21s-7-6.1-7-11.5a7 7 0 0 1 14 0C19 14.9 12 21 12 21z",
        "M12 12.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    )
    val Fuel = lineIcon(
        "Fuel",
        strokeWidth = 2f,
        "M3 22V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v17M3 22h11M14 9h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V8l-3-3M6 8h5",
    )
    val Inbox = lineIcon(
        "Inbox",
        strokeWidth = 2f,
        "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "m22 7-10 6L2 7",
    )
    /** 휴게소 (a cup). */
    val Rest = lineIcon("Rest", strokeWidth = 2.2f, "M17 8h1a4 4 0 0 1 0 8h-1M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4zM6 2v3M10 2v3M14 2v3")
    /** 식사 (fork and knife). */
    val Food = lineIcon(
        "Food",
        strokeWidth = 2.2f,
        "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2M7 2v20M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3zm0 0v7",
    )
    /** 한마디 (a speech bubble). */
    val Shout = lineIcon("Shout", strokeWidth = 2.2f, "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
    /**
     * A magnifier (board 02's search box, target line 156: 26 px, blue). The design's
     * `<circle cx="11" cy="11" r="7">` is written here as two half circles.
     */
    val Search = lineIcon("Search", strokeWidth = 2.4f, "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "m20 20-3.5-3.5")
    /**
     * Board 11's roulette wheel in three colours (the "고민되면 돌려보기" chip, target line 434). It has
     * its own colours, so show it with `tint = Color.Unspecified` (or [CdChip]'s `iconTint = Color.Unspecified`).
     * The design draws it around (0, 0) (`viewBox="-12 -12 24 24"`); here every point is moved by 12.
     */
    val Wheel: ImageVector = ImageVector.Builder(
        name = "Wheel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(addPathNodes("M12 12 L12 1 A11 11 0 0 1 21.5 17.5 Z"), fill = SolidColor(CdColors.Blue))
        addPath(addPathNodes("M12 12 L21.5 17.5 A11 11 0 0 1 2.5 17.5 Z"), fill = SolidColor(CdColors.Orange))
        addPath(addPathNodes("M12 12 L2.5 17.5 A11 11 0 0 1 12 1 Z"), fill = SolidColor(WHEEL_TEAL))
    }.build()

    // The side rail's icons.
    val Home = lineIcon("Home", strokeWidth = 2f, "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Send = lineIcon("Send", strokeWidth = 2f, "M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z")
    val Crew = lineIcon(
        "Crew",
        strokeWidth = 2f,
        "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75",
    )
    val Game = lineIcon(
        "Game",
        strokeWidth = 2f,
        "M6 12h4M8 10v4M15 13h.01M18 11h.01M17.32 5H6.68a4 4 0 0 0-3.98 3.59L2 15a3 3 0 0 0 5.2 2.1L9 15h6l1.8 2.1A3 3 0 0 0 22 15l-.7-6.41A4 4 0 0 0 17.32 5z",
    )
}

/** The wheel's third slice (#11A08D, the design's darker teal; [CdColors.Teal] is #00B8A9). */
private val WHEEL_TEAL = Color(0xFF11A08D)

/** Builds an icon from SVG path strings, drawn as lines (no fill), like the design's `<svg>` tags. */
private fun lineIcon(name: String, strokeWidth: Float, vararg svgPaths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    svgPaths.forEach { path ->
        builder.addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
