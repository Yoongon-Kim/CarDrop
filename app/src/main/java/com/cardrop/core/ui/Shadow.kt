package com.cardrop.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft shadow like the design's CSS `box-shadow: <x> <y> <blur> <colour>`. Material's elevation
 * (`Modifier.shadow`, a card's `elevation`) looks heavier and sits differently, so use this where a
 * board has a `box-shadow`. Copy the numbers straight from the board, e.g. the navigator's turn banner
 * `box-shadow: 0 6px 18px rgba(0,0,0,0.15)`:
 *
 * ```
 * Modifier
 *     .softShadow(RoundedCornerShape(20.dp), Color.Black.copy(alpha = 0.15f), blur = 18.dp, y = 6.dp)
 *     .clip(RoundedCornerShape(20.dp))
 *     .background(CdColors.NavBanner)
 * ```
 *
 * Put it before `clip` / `background`, with the same shape as the thing it belongs to. The shadow is
 * drawn outside the thing's edges, so a parent that clips (`clip`, `clipToBounds`) cuts it off.
 */
fun Modifier.softShadow(
    shape: Shape,
    /** The CSS `rgba(...)`: a colour with its alpha, e.g. `Color.Black.copy(alpha = 0.15f)`. */
    color: Color,
    /** The CSS blur radius (the third number). */
    blur: Dp,
    /** The CSS x offset (the first number); negative = to the left. */
    x: Dp = 0.dp,
    /** The CSS y offset (the second number); positive = downwards. */
    y: Dp = 0.dp,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val paint = Paint().apply {
        asFrameworkPaint().apply {
            // The shape itself is invisible; only its shadow shows. Android takes the shadow's alpha from
            // the shadow colour only when that colour isn't fully opaque, so cap it just below 1.
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                cssBlurToShadowRadius(blur.toPx()),
                x.toPx(),
                y.toPx(),
                color.copy(alpha = color.alpha.coerceAtMost(MAX_SHADOW_ALPHA)).toArgb(),
            )
        }
    }
    onDrawBehind { drawIntoCanvas { it.drawOutline(outline, paint) } }
}

/**
 * CSS blurs a shadow with a Gaussian whose sigma is half the blur radius. Android's `setShadowLayer`
 * takes a radius that it turns into sigma = 0.57735 x radius + 0.5 (Skia's rule), so solve for that radius.
 * A radius of 0 would remove the shadow, so it stays a little above 0.
 */
private fun cssBlurToShadowRadius(blurPx: Float): Float =
    ((blurPx / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private const val MAX_SHADOW_ALPHA = 0.996f
