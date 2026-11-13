package com.cardrop.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarPosition
import com.cardrop.core.ui.*
import com.cardrop.core.ui.theme.CdColors

@Composable
internal fun CrewDriveOverview(
    cars: List<Car>,
    positions: Map<String, CarPosition>,
    myCarId: String,
    fuel: Int? = null,
    /** Shown between the crew strip and the crew cards, e.g. board 06's fuel banner. */
    afterStrip: @Composable () -> Unit = {},
) {
    val located = cars.filter { positions[it.id] != null }.sortedByDescending { positions[it.id]!!.kmToDestination }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(24.dp)).background(CdColors.TealLight)) {
            Text("크루 위치", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = CdColors.TealDark)
            Canvas(Modifier.fillMaxSize()) {
                drawLine(CdColors.Gray600, Offset(20.dp.toPx(), size.height * .7f), Offset(size.width - 20.dp.toPx(), size.height * .7f),
                    strokeWidth = 22.dp.toPx(), cap = StrokeCap.Round)
                drawLine(CdColors.White, Offset(20.dp.toPx(), size.height * .7f), Offset(size.width - 20.dp.toPx(), size.height * .7f),
                    strokeWidth = 2.dp.toPx())
            }
            if (located.isEmpty()) {
                StatusText("차량 위치를 기다리고 있어요", Modifier.align(Alignment.Center))
            } else {
                val back = positions.getValue(located.first().id).kmToDestination
                val front = positions.getValue(located.last().id).kmToDestination
                located.forEachIndexed { i, car ->
                    val remaining = positions.getValue(car.id).kmToDestination
                    val t = if (back - front < .1f) (i + .5f) / located.size else .07f + .86f * (back - remaining) / (back - front)
                    Column(Modifier.offset(x = (maxWidth - 110.dp) * t, y = if (i % 2 == 0) 50.dp else 74.dp).width(110.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(car.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        CarBadge(car, 36)
                    }
                }
            }
        }
        afterStrip()
        cars.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { car ->
                    val position = positions[car.id]
                    CdCard(Modifier.weight(1f)) {
                        InfoRow(car.name, if (position == null) "위치 기다리는 중" else "${position.kmToDestination.toInt()}km 남음 · ${position.etaMinutes}분",
                            leading = { CarBadge(car, 36) })
                        if (car.id == myCarId) Text("내 차 연료 ${fuel ?: "—"}%", color = CdColors.BlueDark)
                    }
                }
            }
        }
    }
}
