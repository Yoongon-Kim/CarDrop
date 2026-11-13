package com.cardrop.drive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Place
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.theme.CdColors
import kotlinx.coroutines.delay

/**
 * What the CarDrop side shows about the crew's stop ([TripCoordinator] keeps it).
 * [atMillis] makes every notice new, so the same stop twice still shows twice.
 */
data class StopNotice(val place: Place, val kind: Kind, val crewTold: Boolean = false, val atMillis: Long) {
    enum class Kind {
        /** This car just got there (within 300 m); not decided yet whether it stops. */
        AT_STOP,

        /** It stopped there; the crew was told. */
        ARRIVED,

        /** It drove past; the crew was told and the route goes to the destination. */
        PASSED,
    }
}

/**
 * Owner: Member B.
 *
 * A card at the top right of the CarDrop side, just below the screen header (`core/CarDropApp.kt` places it):
 * - at the stop: "문막휴게소에 왔어요 · 멈추면 크루에게 도착을 알려요", while that stop is still the waypoint;
 * - stopped / drove past: "문막휴게소에 도착했어요 · 크루에게 알렸어요", for 10 seconds.
 * The map toast and the voice say the same; this card is the one that stays long enough to read.
 */
@Composable
fun StopNoticeCard(notice: StopNotice?, waypoint: Place?) {
    var expired by remember { mutableStateOf<StopNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null && notice.kind != StopNotice.Kind.AT_STOP) {
            delay(SHOW_MILLIS)
            expired = notice
        }
    }
    val visible = notice != null && notice != expired &&
        (notice.kind != StopNotice.Kind.AT_STOP || waypoint?.id == notice.place.id)

    AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
        notice?.let { Card(it) }
    }
}

@Composable
private fun Card(notice: StopNotice) {
    val told = if (notice.crewTold) "크루에게 알렸어요" else "크루에게 알리지 못했어요"
    val (icon, accent, title, detail) = when (notice.kind) {
        StopNotice.Kind.AT_STOP -> Look(CdIcons.Rest, CdColors.Orange, "${notice.place.name}에 왔어요", "멈추면 크루에게 도착을 알려요")
        StopNotice.Kind.ARRIVED -> Look(CdIcons.Check, CdColors.Green, StopNews.arrived(notice.place), told)
        StopNotice.Kind.PASSED -> Look(CdIcons.Send, CdColors.Blue, StopNews.passed(notice.place), "$told · 목적지로 안내해요")
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .width(460.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(CdColors.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = CdColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = CdColors.Gray500, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class Look(val icon: ImageVector, val accent: Color, val title: String, val detail: String)

/** How long the stopped / drove-past card stays. */
private const val SHOW_MILLIS = 10_000L
