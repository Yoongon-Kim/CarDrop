package com.cardrop.navi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import com.cardrop.core.ui.cssBlurToShadowRadius
import com.cardrop.core.ui.theme.NotoSansKr

/**
 * The app's font (Noto Sans KR, `core/ui/theme/Theme.kt`) as the two Android typefaces the marker images use:
 * [bold] (700) for the name tags, [extraBold] (800) for the "경" pin, as in the design (target line 55).
 */
internal class MarkerFonts(val bold: Typeface, val extraBold: Typeface)

/**
 * [MarkerFonts] taken from Compose, so the map's text is the same font file and weights as the app's Text.
 * The font is in the app (`res/font`), so it loads at once and the typefaces are ready here.
 */
@Composable
internal fun rememberMarkerFonts(): MarkerFonts {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) {
        MarkerFonts(
            bold = resolver.resolveAsTypeface(NotoSansKr, FontWeight.Bold).value,
            extraBold = resolver.resolveAsTypeface(NotoSansKr, FontWeight.ExtraBold).value,
        )
    }
}

/**
 * Owner: Member B.
 *
 * The marker images both maps draw ([NavMap] = MapLibre, [KakaoNavMap] = Kakao): this car, the waypoint
 * and destination pins, and the small name tags. Drawn with Android's Canvas, with the app's font ([fonts]).
 * Sizes are in design px; [ratio] is how many real pixels one design px gets.
 */
internal class MapMarkers(private val ratio: Float, private val fonts: MarkerFonts) {

    /** Creates a bitmap of [w] x [h] design px, sharp at our pixel ratio. */
    fun bitmap(w: Float, h: Float, draw: Canvas.() -> Unit): Bitmap =
        Bitmap.createBitmap((w * ratio).toInt(), (h * ratio).toInt(), Bitmap.Config.ARGB_8888).apply {
            density = (DisplayMetrics.DENSITY_DEFAULT * ratio).toInt() // tells MapLibre the pixel ratio
            Canvas(this).apply { scale(ratio, ratio); draw() }
        }

    /**
     * This car: the design's blue dot (target line 57: 30 px, a 4 px white border, so 38 across, and
     * `box-shadow: 0 4px 12px rgba(0,0,0,0.25)`), plus our white arrow showing the driving direction.
     * The image has room around the dot for the shadow.
     */
    fun mePuck(): Bitmap = bitmap(PUCK_SIZE + 2 * SHADOW_ROOM, PUCK_SIZE + 2 * SHADOW_ROOM) {
        translate(SHADOW_ROOM, SHADOW_ROOM)
        val c = PUCK_SIZE / 2 // the centre: 19
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = MARKER_WHITE
        paint.setShadowLayer(cssBlurToShadowRadius(12f), 0f, 4f, 0x40000000) // the shadow comes with the white ring
        drawCircle(c, c, c, paint)
        paint.clearShadowLayer()
        paint.color = MARKER_BLUE
        drawCircle(c, c, 15f, paint)
        paint.color = MARKER_WHITE
        drawPath(
            Path().apply {
                moveTo(c, c - 10f); lineTo(c + 7f, c + 7f); lineTo(c, c + 3f); lineTo(c - 7f, c + 7f); close()
            },
            paint,
        )
    }

    /** The waypoint marker from the design: orange, "경". */
    fun waypointPin(): Bitmap = squarePin(MARKER_ORANGE, "경")

    /** The destination marker from the design: blue, a flag. */
    fun destinationPin(): Bitmap = squarePin(MARKER_BLUE, null)

    /**
     * The design's "경" pin (target line 55): 38 px with a 2 px white border (42 across), corners 12,
     * `box-shadow: 0 4px 12px rgba(0,0,0,0.2)`. The image has room around the pin for the shadow.
     */
    private fun squarePin(color: Int, text: String?): Bitmap = bitmap(PIN_SIZE + 2 * SHADOW_ROOM, PIN_SIZE + 2 * SHADOW_ROOM) {
        translate(SHADOW_ROOM, SHADOW_ROOM)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = MARKER_WHITE
        paint.setShadowLayer(cssBlurToShadowRadius(12f), 0f, 4f, 0x33000000) // drawn only under this white shape
        drawRoundRect(RectF(0f, 0f, PIN_SIZE, PIN_SIZE), 12f, 12f, paint)
        paint.clearShadowLayer()
        paint.color = color
        drawRoundRect(RectF(2f, 2f, PIN_SIZE - 2f, PIN_SIZE - 2f), 10f, 10f, paint)
        paint.color = MARKER_WHITE
        if (text != null) {
            paint.textSize = 14f
            paint.typeface = fonts.extraBold // the design's 14/800
            paint.textAlign = Paint.Align.CENTER
            drawText(text, 21f, 21f - (paint.ascent() + paint.descent()) / 2, paint)
        } else {
            // Flag icon (design: M4 22V4 M4 4h12l-2 4 2 4H4), 20 px, centred.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            save()
            translate(11f, 10f)
            scale(20f / 24f, 20f / 24f)
            drawPath(Path().apply { moveTo(4f, 22f); lineTo(4f, 4f) }, paint)
            drawPath(Path().apply { moveTo(4f, 4f); lineTo(16f, 4f); lineTo(14f, 8f); lineTo(16f, 12f); lineTo(4f, 12f) }, paint)
            restore()
        }
    }

    /** A small white name tag under a marker ("삼촌", "여주휴게소"). */
    fun labelPill(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = fonts.bold
        }
        val width = paint.measureText(text) + 16f
        return bitmap(width + 2f, 24f) {
            paint.color = 0x26000000
            drawRoundRect(RectF(1f, 2f, width + 1f, 24f), 8f, 8f, paint)
            paint.color = MARKER_WHITE
            drawRoundRect(RectF(1f, 1f, width + 1f, 22f), 8f, 8f, paint)
            paint.color = MARKER_INK
            drawText(text, 9f, 11.5f - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
}

/** Map labels and markers a bit larger than the UI scale, so they read on the car screen. */
internal const val MAP_SCALE = 1.25f

/** The waypoint and destination pins: 42 design px across. */
private const val PIN_SIZE = 42f

/** This car's dot: 38 design px across (its white ring included). */
private const val PUCK_SIZE = 38f

/** Room around a marker for its soft shadow (the design's 12 px blur + 4 px offset). */
private const val SHADOW_ROOM = 16f

/** How far below a pin's centre its name tag starts (design px): just under the 42 px pin. */
internal const val PIN_TAG_GAP = 24f

internal const val MARKER_WHITE = 0xFFFFFFFF.toInt()
internal const val MARKER_BLUE = 0xFF2F6FE0.toInt()
internal const val MARKER_ORANGE = 0xFFFF8A3D.toInt()
internal const val MARKER_INK = 0xFF191F28.toInt()
