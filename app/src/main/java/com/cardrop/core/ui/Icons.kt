package com.cardrop.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

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
    val Fuel = lineIcon(
        "Fuel",
        strokeWidth = 2f,
        "M3 22V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v17M3 22h11M14 9h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V8l-3-3M6 8h5",
    )
}

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
