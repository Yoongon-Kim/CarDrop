package com.cardrop.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.cardrop.core.model.Car
import com.cardrop.core.navigation.Screen
import com.cardrop.core.ui.theme.CdColors

/** Every screen's outer layout: padding from the design (24 x 28) and vertical scrolling. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The left menu ("rail") from the design. 기록 (safety race) is cut for week 1, so 편지함 takes its place. */
private data class RailItem(val label: String, val icon: ImageVector, val screen: Screen)

private val railItems = listOf(
    RailItem("홈", CdIcons.Home, Screen.Drive),
    RailItem("드랍", CdIcons.Send, Screen.DropSend),
    RailItem("크루", CdIcons.Crew, Screen.CreateCrew),
    RailItem("게임", CdIcons.Game, Screen.Games),
    RailItem("편지함", CdIcons.Inbox, Screen.Inbox),
)

private fun Screen.railGroup(): Screen = when (this) {
    Screen.Drive, Screen.FuelMe, is Screen.FuelThem, is Screen.Decided -> Screen.Drive
    Screen.DropSend, is Screen.Shout, is Screen.AiRecommend, is Screen.Vote -> Screen.DropSend
    Screen.CreateCrew, Screen.DestinationSearch, Screen.JoinCrew -> Screen.CreateCrew
    Screen.Games -> Screen.Games
    Screen.Inbox -> Screen.Inbox
}

/**
 * Layout of the whole app window: the whole 1600 x 900 design, [navPanel] (the in-app navigator) on the
 * left 1/3, CarDrop on the right 2/3.
 */
@Composable
fun CarDropShell(
    current: Screen,
    onTab: (Screen) -> Unit,
    navPanel: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    /** This car, drawn at the bottom of the rail with "내 차" (as in the design). */
    myCar: Car? = null,
    content: @Composable () -> Unit,
) {
    DesignScale(designWidth = DESIGN_WIDTH + NAV_PANEL_WIDTH) {
        Row(modifier = Modifier.fillMaxSize().background(CdColors.Gray100)) {
            Box(modifier = Modifier.width(NAV_PANEL_WIDTH.dp).fillMaxHeight()) { navPanel() }
            Rail(current = current.railGroup(), onTab = onTab, myCar = myCar)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content()
                overlay()
            }
        }
    }
}

/**
 * The design's CarDrop area is 1067 x 900 px (1600 x 900 minus the 533 px navigation third).
 * This scales the UI so 1 dp in our code = 1 design px when the window is [designWidth] x 900, and
 * grows or shrinks everything together on other windows. Write sizes straight from the design boards.
 */
@Composable
private fun DesignScale(designWidth: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val current = LocalDensity.current
        val scale = minOf(maxWidth.value / designWidth, maxHeight.value / DESIGN_HEIGHT)
        CompositionLocalProvider(
            LocalDensity provides Density(current.density * scale, current.fontScale),
            content = content,
        )
    }
}

private const val DESIGN_WIDTH = 1067f
private const val DESIGN_HEIGHT = 900f
private const val NAV_PANEL_WIDTH = 533f

@Composable
private fun Rail(current: Screen, onTab: (Screen) -> Unit, myCar: Car?) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(CdColors.White)
            // The design's thin line between the navigator and CarDrop.
            .drawBehind { drawLine(CdColors.Gray200, Offset.Zero, Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(CdColors.Blue),
            contentAlignment = Alignment.Center,
        ) {
            Text("CD", color = CdColors.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        railItems.forEach { item ->
            val on = item.screen == current
            Column(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) CdColors.BlueLight else CdColors.White)
                    .clickable { onTab(item.screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (on) CdColors.BlueDark else CdColors.Gray500
                Icon(item.icon, contentDescription = null, tint = tint)
                Text(item.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (myCar != null) {
            Spacer(Modifier.weight(1f))
            CarBadge(myCar)
            Text("내 차", color = CdColors.Gray600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
