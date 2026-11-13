package com.cardrop.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor

/**
 * The little car from the design boards, drawn in the car's colour. Used wherever a car appears.
 *
 * [size] works like the design's badge size: the car is `size x 1.65` wide and `size x 1.1` tall.
 * A connected car bobs gently, as in the design; with `bob = false` it stands still (board 15's opponent
 * chips: `size = 24` ≈ the design's 40 x 27, target line 740). A car that hasn't joined yet is grey and faded.
 */
@Composable
fun CarBadge(car: Car, size: Int = 40, bob: Boolean = true) {
    val color = if (car.connected) car.color else CarColor.GRAY
    val bobMillis = color.bobMillis
    // Read inside graphicsLayer, so the bobbing only redraws the car, not the screen around it.
    val lift = if (bob && bobMillis != null) {
        rememberInfiniteTransition(label = "carBob").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(bobMillis / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "lift",
        )
    } else {
        null
    }
    val (top, bottom) = color.gradient()
    Canvas(
        Modifier
            .size((size * 1.65f).dp, (size * 1.1f).dp)
            .graphicsLayer {
                translationY = -BOB_HEIGHT.toPx() * (lift?.value ?: 0f)
                alpha = if (bobMillis == null) 0.7f else 1f
            },
    ) { drawCar(top, bottom) }
}

/** Draws the design's 120 x 80 car (same shapes as its SVG), scaled to the canvas. */
private fun DrawScope.drawCar(top: Color, bottom: Color) {
    val paint = Brush.verticalGradient(listOf(top, bottom), startY = 17f, endY = 61f)
    scale(size.width / 120f, size.height / 80f, pivot = Offset.Zero) {
        drawOval(SHADOW, topLeft = Offset(16f, 63f), size = Size(88f, 14f))
        drawPath(CarShapes.body, paint)
        drawPath(CarShapes.glass, GLASS)
        drawLine(paint, Offset(65f, 21f), Offset(65f, 35f), strokeWidth = 3f)
        drawPath(CarShapes.shine, Color.White.copy(alpha = 0.6f), style = Stroke(width = 4f, cap = StrokeCap.Round))
        for (x in listOf(34f, 88f)) {
            drawCircle(TYRE, radius = 10f, center = Offset(x, 60f))
            drawCircle(HUB, radius = 4f, center = Offset(x, 60f))
        }
        drawCircle(HEADLIGHT, radius = 3f, center = Offset(106f, 46f))
    }
}

/** The design's SVG paths, parsed once on first use. */
private object CarShapes {
    val body by lazy { path("M14 52 Q12 40 26 37 L40 24 Q46 17 58 17 L76 17 Q88 17 94 27 L102 37 Q112 39 110 52 Q110 61 100 61 L22 61 Q14 61 14 52Z") }
    val glass by lazy { path("M44 26 Q48 21 56 21 L74 21 Q83 21 87 28 L91 35 L40 35 Z") }
    val shine by lazy { path("M24 42 Q40 39 60 40") }
    private fun path(svg: String) = PathParser().parsePathString(svg).toPath()
}

/** Top and bottom colour of the body, from the design's gradients (g-blue, g-purple, ...). */
private fun CarColor.gradient(): Pair<Color, Color> = when (this) {
    CarColor.BLUE -> Color(0xFF8CC2FF) to Color(0xFF2F6FE0)
    CarColor.PURPLE -> Color(0xFFC6B2FF) to Color(0xFF7A4FE0)
    CarColor.TEAL -> Color(0xFF8CEBDD) to Color(0xFF11A08D)
    CarColor.YELLOW -> Color(0xFFFFE59A) to Color(0xFFF0A30A)
    CarColor.GRAY -> Color(0xFFEEF0F3) to Color(0xFFB0B8C1)
}

/** One up-and-down bob, as in the design (each colour a little different, so cars don't move in step). */
private val CarColor.bobMillis: Int?
    get() = when (this) {
        CarColor.BLUE -> 1300
        CarColor.PURPLE -> 1500
        CarColor.TEAL -> 1400
        CarColor.YELLOW -> 1200
        CarColor.GRAY -> null
    }

private val BOB_HEIGHT = 6.dp
private val SHADOW = Color(0x24191F28)
private val GLASS = Color(0xFFE6F1FF)
private val TYRE = Color(0xFF2B2F36)
private val HUB = Color(0xFFD1D6DB)
private val HEADLIGHT = Color(0xFFFFF3B0)
