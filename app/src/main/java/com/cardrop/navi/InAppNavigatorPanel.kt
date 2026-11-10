package com.cardrop.navi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drive.DriveViewModel
import com.cardrop.drive.NavArrow
import com.cardrop.drive.RoutePoint
import kotlinx.coroutines.delay

/**
 * Owner: Member B.
 *
 * The navigator drawn inside CarDrop (`cardrop.navApp=inapp`): the design's left third
 * (533 x 900 design px). A real map with the live route (GPS + Kakao/OSRM) and every crew car,
 * plus the design's overlays: turn banner, CarDrop toast, speed, and the waypoint / destination card.
 */
@Composable
fun InAppNavigatorPanel(nav: InAppNavigation, vm: DriveViewModel) {
    val route by vm.navRoute.collectAsStateWithLifecycle()
    val positions by vm.positions.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val guiding by nav.guiding.collectAsStateWithLifecycle()
    val navState by nav.state.collectAsStateWithLifecycle()

    val state = navPanelState(route, navState, guiding, System.currentTimeMillis())
    // This car: on the route when on it, else the raw GPS point.
    val me = navState.snapped ?: navState.fix?.let { RoutePoint(it.lat, it.lng, it.bearing ?: 0.0) }
    val cars = crew?.cars.orEmpty().filterNot { it.isMe }.mapNotNull { car ->
        positions[car.id]?.let { MapCar(car.id, shortName(car.name), car.color, it) }
    }

    // "전체 경로" button: the whole trip instead of following this car.
    var overview by remember { mutableStateOf(false) }
    // The driver dragged or zoomed the map: stop following until "현재 위치".
    var free by remember { mutableStateOf(false) }
    // When the crew picks a stop, show it next to this car for a few seconds, then follow again.
    var showWaypoint by remember { mutableStateOf(false) }
    LaunchedEffect(route.waypoint?.id) {
        if (route.waypoint != null) {
            showWaypoint = true
            delay(5_000)
            showWaypoint = false
        }
    }
    val camera = when {
        free -> NavCamera.FREE
        me == null || !guiding || overview -> NavCamera.OVERVIEW
        showWaypoint && route.waypoint != null -> NavCamera.WAYPOINT
        else -> NavCamera.FOLLOW
    }

    // The CarDrop toast under the banner shows each new route change for 5 seconds.
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route.lastAction) {
        val text = route.lastAction ?: return@LaunchedEffect
        toast = text
        delay(5_000)
        toast = null
    }

    Box(modifier = Modifier.fillMaxSize().background(CdColors.NavBackground)) {
        NavMap(
            route = navState.route,
            progressKm = navState.progressKm,
            me = me,
            cars = cars,
            waypoint = route.waypoint,
            destination = route.destination,
            camera = camera,
            onUserMovedMap = { free = true },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Banner(state.banner)
            AnimatedVisibility(visible = toast != null, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                Toast(toast.orEmpty())
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "© OpenFreeMap © OpenStreetMap" + (state.provider?.let { " · 경로 $it" } ?: ""),
                    color = CdColors.Gray500,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                when {
                    free -> MapButton("현재 위치") { free = false; overview = false }
                    me != null && guiding -> MapButton(if (overview) "내 차 보기" else "전체 경로") { overview = !overview }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                Speed(state.speedKmh)
                EtaCard(state, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Banner(banner: NavBanner) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CdColors.NavBanner)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TurnArrow(banner.arrow, Modifier.size(40.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (banner.distance.isNotEmpty()) {
                Text(banner.distance, color = CdColors.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
            }
            Text(
                banner.text,
                color = CdColors.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Toast(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(CdColors.White)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag("CarDrop", CdColors.Blue)
        Text(text, color = CdColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
private fun Speed(kmh: Int) {
    Column(
        modifier = Modifier.size(92.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(CdColors.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$kmh", color = CdColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
        Text("km/h", color = CdColors.Gray600, fontSize = 12.sp)
    }
}

@Composable
private fun EtaCard(state: NavPanelState, modifier: Modifier) {
    Column(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(CdColors.White.copy(alpha = 0.97f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.waypoint?.let { EtaLine("경유", CdColors.Orange, it) }
        EtaLine("목적지", NavBlue, state.destination ?: EtaRow("목적지를 정해 주세요", ""))
    }
}

@Composable
private fun EtaLine(tag: String, color: Color, row: EtaRow) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(tag, color)
        Text(
            row.name,
            color = CdColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(row.eta, color = CdColors.Gray600, fontSize = 14.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = CdColors.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun MapButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = CdColors.Ink2,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(CdColors.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Banner arrows, drawn on a 24 x 24 grid like the design's SVG icons. */
@Composable
private fun TurnArrow(arrow: NavArrow, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        fun path(build: Path.() -> Unit) = Path().apply(build)
        fun Path.m(x: Float, y: Float) = moveTo(x * unit, y * unit)
        fun Path.l(x: Float, y: Float) = lineTo(x * unit, y * unit)
        fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * unit, cy * unit, x * unit, y * unit)
        val paths = when (arrow) {
            NavArrow.STRAIGHT -> listOf(path { m(12f, 20f); l(12f, 5f) }, path { m(5f, 12f); l(12f, 5f); l(19f, 12f) })
            NavArrow.LEFT -> listOf(path { m(16f, 21f); l(16f, 11f); q(16f, 8f, 13f, 8f); l(5f, 8f) }, path { m(9f, 4f); l(5f, 8f); l(9f, 12f) })
            NavArrow.RIGHT -> listOf(path { m(8f, 21f); l(8f, 11f); q(8f, 8f, 11f, 8f); l(19f, 8f) }, path { m(15f, 4f); l(19f, 8f); l(15f, 12f) })
            NavArrow.UTURN -> listOf(path { m(16f, 21f); l(16f, 9f); q(16f, 4f, 12f, 4f); q(8f, 4f, 8f, 9f); l(8f, 15f) }, path { m(4.5f, 11.5f); l(8f, 15f); l(11.5f, 11.5f) })
            NavArrow.ARRIVE -> listOf(path { m(6f, 22f); l(6f, 4f) }, path { m(6f, 4f); l(18f, 4f); l(16f, 8f); l(18f, 12f); l(6f, 12f) })
        }
        val stroke = Stroke(width = 2.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        paths.forEach { drawPath(it, Color.White, style = stroke) }
    }
}

/** "삼촌 차" -> "삼촌", "아빠 차 (나)" -> "아빠". */
private fun shortName(name: String) = name.substringBefore(" (").removeSuffix(" 차")

/** The design's route blue (left third), a little deeper than the CarDrop blue. */
private val NavBlue = Color(0xFF2F6FE0)
