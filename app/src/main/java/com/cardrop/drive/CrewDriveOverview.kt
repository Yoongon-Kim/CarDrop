package com.cardrop.drive

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.CarPosition
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.tint
import java.util.Locale
import kotlin.math.roundToInt

/** A car on board 04's crew strip: how far along the road it is (0 = left, 1 = right), its rank and minutes to go. */
data class StripCar(val car: Car, val along: Float, val rank: Int, val minutes: Int)

/**
 * Places the cars that have a position on the strip, like the design: the car furthest back at 13 %, the leader at
 * 86 %, the others by distance in between (spread evenly when they are all at the same spot). Rank 1 = the leader.
 */
fun crewStrip(cars: List<Car>, positions: Map<String, CarPosition>): List<StripCar> {
    val located = cars.mapNotNull { car -> positions[car.id]?.let { car to it } }.sortedBy { it.second.kmToDestination }
    if (located.isEmpty()) return emptyList()
    val front = located.first().second.kmToDestination
    val back = located.last().second.kmToDestination
    return located.mapIndexed { i, (car, position) ->
        val along = if (back - front < 0.1f) {
            STRIP_END - (STRIP_END - STRIP_START) * i / (located.size - 1).coerceAtLeast(1)
        } else {
            STRIP_START + (STRIP_END - STRIP_START) * (back - position.kmToDestination) / (back - front)
        }
        StripCar(car, along, rank = i + 1, minutes = position.etaMinutes)
    }
}

/**
 * The order to draw the strip's cars in: the last car first, the leader last. A Box draws its children in order,
 * so when two markers overlap, the car in front ends up on top.
 */
fun drawingOrder(strip: List<StripCar>): List<StripCar> = strip.sortedByDescending { it.rank }

/** The distance along the shared route between two neighbouring cars, from front to back. */
data class CrewGap(val ahead: Car, val behind: Car, val distanceKm: Float)

fun crewGaps(strip: List<StripCar>, positions: Map<String, CarPosition>): List<CrewGap> =
    strip.zipWithNext { ahead, behind ->
        CrewGap(
            ahead.car,
            behind.car,
            (positions.getValue(behind.car.id).kmToDestination - positions.getValue(ahead.car.id).kmToDestination).coerceAtLeast(0f),
        )
    }

/** Coincident cars keep a zero gap even when the diagram spreads their markers out for readability. */
fun gapDistanceText(distanceKm: Float): String {
    if (!distanceKm.isFinite()) return "확인 중"
    val meters = (distanceKm.coerceAtLeast(0f) * 1_000).roundToInt()
    return if (meters < 1_000) "${meters}m" else String.format(Locale.US, "%.1fkm", distanceKm)
}

/** "선두" for the leading car, otherwise how far behind it, e.g. "2.1km 뒤". */
fun gapText(leaderKmToDestination: Float, kmToDestination: Float): String {
    val behind = kmToDestination - leaderKmToDestination
    return when {
        behind < 0.05f -> "선두"
        behind < 10f -> String.format(Locale.US, "%.1fkm 뒤", behind)
        else -> "${behind.roundToInt()}km 뒤"
    }
}

/**
 * Board 04 "크루 위치": the design's road through green scenery, lane dashes moving as if the crew drives,
 * a finish line, and every car on the road with its rank and minutes to go (this car's pill is blue).
 * The bottom row gives the real route distance between adjacent cars, in the same left-to-right order.
 */
@Composable
internal fun CrewRoad(cars: List<Car>, positions: Map<String, CarPosition>, myCarId: String, destinationName: String?) {
    val strip = crewStrip(cars, positions)
    val gaps = crewGaps(strip, positions).asReversed()
    val roadHeight = 200.dp
    val footerHeight = if (gaps.isEmpty()) 0.dp else 48.dp
    val dashPhase by rememberInfiniteTransition(label = "lanes").animateFloat(
        initialValue = 0f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing)),
        label = "dashPhase",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(roadHeight + footerHeight).clip(RoundedCornerShape(24.dp)).background(GRASS)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = with(LocalDensity.current) { roadHeight.toPx() }
        // dashPhase is read while drawing, so the moving lanes redraw only the canvas.
        Canvas(Modifier.fillMaxWidth().height(roadHeight)) { drawScenery(dashPhase) }
        StripLabel("크루 위치", CdColors.Ink, 14, Modifier.align(Alignment.TopStart))
        destinationName?.let { StripLabel("$it 방면 →", CdColors.Green, 13, Modifier.align(Alignment.TopEnd)) }
        if (strip.isEmpty()) StatusText("차량 위치를 기다리고 있어요", Modifier.align(Alignment.Center))
        drawingOrder(strip).forEach { car ->
            val x = car.along * VIEW_WIDTH
            val centerX = (x * widthPx / VIEW_WIDTH).roundToInt()
            val roadY = (RoadShape.yAt(x) * heightPx / VIEW_HEIGHT).roundToInt()
            CarMarker(
                car,
                isMe = car.car.id == myCarId,
                // The car stands on the road (its bottom 10 dp below the road's centre line), centred on its spot.
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(centerX - placeable.width / 2, roadY - placeable.height + 10.dp.roundToPx())
                    }
                },
            )
        }
        if (gaps.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gaps.forEach { gap ->
                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(CdColors.White.copy(alpha = 0.92f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${gap.behind.name} ↔ ${gap.ahead.name}",
                            modifier = Modifier.weight(1f),
                            color = CdColors.Gray600, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(gapDistanceText(gap.distanceKm), color = CdColors.BlueDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StripLabel(text: String, color: Color, fontSize: Int, modifier: Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CdColors.White.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun CarMarker(car: StripCar, isMe: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .shadow(4.dp, shape)
                .clip(shape)
                .background(if (isMe) CdColors.Blue else CdColors.White)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(car.car.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (isMe) CdColors.White else CdColors.Ink, maxLines = 1)
            Text(
                "${car.rank}번째 · ${car.minutes}분",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isMe) CdColors.White else CdColors.BlueDark, maxLines = 1,
            )
        }
        CarBadge(car.car)
    }
}

/** The design's strip drawing (1000 x 200 box, stretched to fit like its SVG): trees, road, lane dashes, finish line. */
private fun DrawScope.drawScenery(dashPhase: Float) {
    scale(size.width / VIEW_WIDTH, size.height / VIEW_HEIGHT, pivot = Offset.Zero) {
        TREES.forEach { tree -> drawCircle(tree.color, tree.radius, Offset(tree.x, tree.y)) }
        drawPath(RoadShape.path, ROAD, style = Stroke(width = 40f))
        drawPath(
            RoadShape.path,
            Color.White,
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 20f), dashPhase)),
        )
        drawRect(Color.White, topLeft = Offset(972f, 86f), size = Size(12f, 56f))
    }
}

/** The road from the design's SVG, and its height at any x (to stand the cars on it). */
private object RoadShape {
    val path by lazy {
        PathParser().parsePathString(
            "M-40 150 C65 150 90 132 130 132 S300 172 370 159 S510 106 610 124 S780 175 860 140 S965 102 1040 116",
        ).toPath()
    }
    private val samples by lazy {
        val measure = PathMeasure().apply { setPath(path, false) }
        (0..400).map { measure.getPosition(measure.length * it / 400f) }
    }

    fun yAt(x: Float): Float {
        val i = samples.indexOfFirst { it.x >= x }
        if (i <= 0) return samples.first().y
        val a = samples[i - 1]
        val b = samples[i]
        val t = if (b.x == a.x) 0f else (x - a.x) / (b.x - a.x)
        return a.y + (b.y - a.y) * t
    }
}

private class Tree(val x: Float, val y: Float, val radius: Float, val color: Color)

private val TREES = listOf(
    Tree(90f, 70f, 16f, Color(0xFF5DBB7A)), Tree(300f, 62f, 20f, Color(0xFF4FAE6E)), Tree(560f, 72f, 14f, Color(0xFF6FC98A)),
    Tree(780f, 58f, 18f, Color(0xFF5DBB7A)), Tree(210f, 190f, 14f, Color(0xFF6FC98A)), Tree(690f, 194f, 16f, Color(0xFF4FAE6E)),
)
private val GRASS = Color(0xFFDFF3E6)
private val ROAD = Color(0xFF3E4652)
private const val VIEW_WIDTH = 1000f
private const val VIEW_HEIGHT = 200f
private const val STRIP_START = 0.13f
private const val STRIP_END = 0.86f

/**
 * Board 04 "우리 일행": a card per car in its colour, with how far behind the leader it is and its fuel ([fuel], by
 * car id: drive/CrewFuel.kt). Low on fuel a card turns orange ("연료 경고" and the range left); tapping this car's
 * opens board 05 ([onMyLowFuel]).
 */
@Composable
internal fun CrewCards(
    cars: List<Car>,
    positions: Map<String, CarPosition>,
    myCarId: String,
    fuel: Map<String, CarFuel>,
    onMyLowFuel: (() -> Unit)?,
) {
    val leaderKm = positions.values.minOfOrNull { it.kmToDestination }
    // Two columns, 10 apart both ways (the design's grid with `gap: 10px`, target 254).
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cars.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { car ->
                    CrewCard(
                        car = car,
                        position = positions[car.id],
                        leaderKm = leaderKm,
                        fuel = fuel[car.id],
                        onLowFuel = if (car.id == myCarId) onMyLowFuel else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CrewCard(
    car: Car,
    position: CarPosition?,
    leaderKm: Float?,
    fuel: CarFuel?,
    onLowFuel: (() -> Unit)?,
    modifier: Modifier,
) {
    val low = fuel?.isLow == true
    val lowRangeKm = fuel?.takeIf { it.isLow }?.rangeKm
    val tint = (if (car.connected) car.color else CarColor.GRAY).tint()
    val shape = RoundedCornerShape(22.dp)
    val gap = when {
        position == null || leaderKm == null -> "위치 기다리는 중"
        lowRangeKm != null -> "${gapText(leaderKm, position.kmToDestination)} · ${lowRangeKm}km 주행 가능"
        else -> gapText(leaderKm, position.kmToDestination)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .border(2.dp, if (low) CdColors.FuelLow else tint, shape)
            .then(if (low && onLowFuel != null) Modifier.clickable(onClick = onLowFuel) else Modifier)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A little bigger than the design's 16 / 13 so the names can be read from the seat (Member A, 2026-11-15).
        CarBadge(car, size = 58)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(car.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, maxLines = 1)
            Text(gap, fontSize = 14.sp, color = CdColors.Gray600, maxLines = 1)
        }
        if (fuel != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (low) "연료 경고" else "연료 ${fuel.percent}%",
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (low) CdColors.OrangeDark else CdColors.Gray600,
                )
                Box(Modifier.width(84.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fuel.percent.coerceIn(0, 100) / 100f).background(if (low) CdColors.Orange else CdColors.Blue))
                }
            }
        }
    }
}
