package com.cardrop.drop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Candidate
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One colour per slice, in candidate order (design board 11). */
private val SliceColors = listOf(CdColors.Blue, CdColors.Orange, CdColors.Teal, CdColors.Purple, CdColors.Yellow)

/**
 * Board 11 돌림판 이스터에그 · Owner: Member A.
 *
 * "고민되면 돌려보기": spins a wheel of the candidates and suggests one for **this car's** vote only.
 * It never sends a vote by itself: "내 표로 넣기" just selects the candidate, and the driver still
 * presses 응답하기. Shown only when there are at least 2 candidates.
 */
@Composable
fun VoteRoulette(
    candidates: List<Candidate>,
    onPick: (candidateId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = candidates.size
    if (count < 2) return

    val scope = rememberCoroutineScope()
    // The wheel's angle in degrees, clockwise. Animatable = a number that can move smoothly over time.
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Candidate?>(null) }

    CdCard(modifier = modifier.fillMaxWidth()) {
        Text("내 표만 정해줘요 · 다른 차 투표엔 영향 없음", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdColors.Gray600)

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(WheelSize.dp), contentAlignment = Alignment.Center) {
                // The wheel and its numbers turn together.
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.value },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sweep = 360f / count
                        candidates.indices.forEach { i ->
                            // Compose counts angles from 3 o'clock; -90 starts slice 0 at 12 o'clock.
                            drawArc(color = SliceColors[i % SliceColors.size], startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true)
                            drawArc(color = CdColors.White, startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true, style = Stroke(width = 3.dp.toPx()))
                        }
                        drawCircle(color = CdColors.White, radius = 14.dp.toPx())
                    }
                    candidates.indices.forEach { i ->
                        val mid = Math.toRadians((RouletteMath.sliceCenter(i, count) - 90f).toDouble())
                        val r = WheelSize * 0.3f
                        Text(
                            text = "${i + 1}",
                            color = CdColors.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.offset(x = (r * cos(mid)).toFloat().dp, y = (r * sin(mid)).toFloat().dp),
                        )
                    }
                }
                // The pointer at the top stays still.
                Canvas(modifier = Modifier.size(22.dp, 18.dp).align(Alignment.TopCenter).offset(y = (-8).dp)) {
                    val tip = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    }
                    drawPath(tip, CdColors.Ink)
                }
            }
        }

        Text(
            text = when {
                spinning -> "돌리는 중…"
                result != null -> "돌림판이 ${result!!.place.name.particle("을", "를")} 골랐어요"
                else -> "못 고르겠으면 돌려봐요"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CdColors.Ink,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "닫기", onClick = onClose, modifier = Modifier.weight(1f))
            val picked = result
            if (picked != null && !spinning) {
                PrimaryButton(text = "내 표로 넣기", onClick = { onPick(picked.id) }, modifier = Modifier.weight(2f))
            } else {
                PrimaryButton(
                    text = "돌리기",
                    enabled = !spinning,
                    modifier = Modifier.weight(2f),
                    onClick = {
                        val winner = Random.nextInt(count)
                        spinning = true
                        result = null
                        scope.launch {
                            rotation.animateTo(
                                targetValue = RouletteMath.targetRotation(rotation.value, winner, count),
                                // Fast start, long slow stop - like a real wheel (same curve as the design).
                                animationSpec = tween(durationMillis = 2600, easing = CubicBezierEasing(0.17f, 0.67f, 0.21f, 1f)),
                            )
                            result = candidates[RouletteMath.landedIndex(rotation.value, count)]
                            spinning = false
                        }
                    },
                )
            }
        }
    }
}

private const val WheelSize = 180f

/**
 * The wheel's arithmetic, kept apart from the drawing so it can be unit tested (RouletteMathTest).
 * Angles are degrees, clockwise, 0 = 12 o'clock. Slice i covers [i, i + 1) * (360 / count).
 */
object RouletteMath {

    /** The middle of slice [index], before the wheel turns. */
    fun sliceCenter(index: Int, count: Int): Float = (index + 0.5f) * 360f / count

    /** Which slice sits under the top pointer after the wheel turned [rotation] degrees clockwise. */
    fun landedIndex(rotation: Float, count: Int): Int {
        val underPointer = ((360f - rotation % 360f) % 360f + 360f) % 360f
        return (underPointer / (360f / count)).toInt().coerceIn(0, count - 1)
    }

    /**
     * The angle to spin to from [current] so slice [winner]'s middle stops under the pointer,
     * after [turns] full turns so it looks like a real spin.
     */
    fun targetRotation(current: Float, winner: Int, count: Int, turns: Int = 4): Float {
        val wantedMod = ((360f - sliceCenter(winner, count)) % 360f + 360f) % 360f
        val currentMod = ((current % 360f) + 360f) % 360f
        val extra = ((wantedMod - currentMod) % 360f + 360f) % 360f
        return current + turns * 360f + extra
    }
}
