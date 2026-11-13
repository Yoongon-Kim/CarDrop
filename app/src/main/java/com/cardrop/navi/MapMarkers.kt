package com.cardrop.navi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics

/**
 * Owner: Member B.
 *
 * The marker images both maps draw ([NavMap] = MapLibre, [KakaoNavMap] = Kakao): this car, the waypoint
 * and destination pins, and the small name tags. Drawn with Android's Canvas so Korean text uses the
 * system font. Sizes are in design px; [ratio] is how many real pixels one design px gets.
 */
internal class MapMarkers(private val ratio: Float) {

    /** Creates a bitmap of [w] x [h] design px, sharp at our pixel ratio. */
    fun bitmap(w: Float, h: Float, draw: Canvas.() -> Unit): Bitmap =
        Bitmap.createBitmap((w * ratio).toInt(), (h * ratio).toInt(), Bitmap.Config.ARGB_8888).apply {
            density = (DisplayMetrics.DENSITY_DEFAULT * ratio).toInt() // tells MapLibre the pixel ratio
            Canvas(this).apply { scale(ratio, ratio); draw() }
        }

    /** This car: the design's blue dot with a white arrow showing the driving direction. */
    fun mePuck(): Bitmap = bitmap(48f, 48f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x40000000
        drawCircle(24f, 26f, 20f, paint)
        paint.color = MARKER_WHITE
        drawCircle(24f, 24f, 20f, paint)
        paint.color = MARKER_BLUE
        drawCircle(24f, 24f, 15f, paint)
        paint.color = MARKER_WHITE
        drawPath(
            Path().apply {
                moveTo(24f, 14f); lineTo(31f, 31f); lineTo(24f, 27f); lineTo(17f, 31f); close()
            },
            paint,
        )
    }

    /** The waypoint marker from the design: orange, "경". */
    fun waypointPin(): Bitmap = squarePin(MARKER_ORANGE, "경")

    /** The destination marker from the design: blue, a flag. */
    fun destinationPin(): Bitmap = squarePin(MARKER_BLUE, null)

    private fun squarePin(color: Int, text: String?): Bitmap = bitmap(42f, 42f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        drawRoundRect(RectF(2f, 4f, 40f, 42f), 13f, 13f, paint)
        paint.color = MARKER_WHITE
        drawRoundRect(RectF(2f, 2f, 40f, 40f), 13f, 13f, paint)
        paint.color = color
        drawRoundRect(RectF(4f, 4f, 38f, 38f), 11f, 11f, paint)
        paint.color = MARKER_WHITE
        if (text != null) {
            paint.textSize = 15f
            paint.typeface = Typeface.DEFAULT_BOLD
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
            typeface = Typeface.DEFAULT_BOLD
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

internal const val MARKER_WHITE = 0xFFFFFFFF.toInt()
internal const val MARKER_BLUE = 0xFF2F6FE0.toInt()
internal const val MARKER_ORANGE = 0xFFFF8A3D.toInt()
internal const val MARKER_INK = 0xFF191F28.toInt()
