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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.cardrop.core.model.Car
import com.cardrop.core.navigation.Screen
import com.cardrop.core.ui.theme.CdColors

/**
 * Every screen's outer layout: padding from the design (24 x 28), [gap] between the items (the design's
 * `gap: 14px`) and vertical scrolling.
 *
 * [bottom] is for what a board pins to the bottom with `margin-top: auto` (01's 출발, 04's 드랍하기,
 * 11's 응답하기, ...). It sits at the bottom of the screen while the content is short, and follows the
 * content (still [gap] below it) when the content is longer than the screen, so it scrolls along.
 *
 * The column is at least as tall as the screen (`fillMaxSize()` comes before `verticalScroll()`), so an
 * item with `Modifier.weight(1f)` takes the height that is left over, like the design's `flex: 1`.
 */
@Composable
fun ScreenColumn(
    gap: Dp = 14.dp,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrangement = remember(gap, bottom != null) {
        if (bottom != null) LastItemAtBottom(gap) else Arrangement.spacedBy(gap)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = arrangement,
    ) {
        content()
        if (bottom != null) {
            // One item, so the arrangement can move it down as a whole.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap), content = bottom)
        }
    }
}

/**
 * Places the items from the top with [gap] between them (like `Arrangement.spacedBy(gap)`), then moves
 * the last item down to the bottom of the column if there is room: the design's `margin-top: auto`.
 * Internal (not private) only so LastItemAtBottomTest can check it.
 */
internal class LastItemAtBottom(private val gap: Dp) : Arrangement.Vertical {
    override val spacing: Dp get() = gap

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        var y = 0
        sizes.forEachIndexed { i, size ->
            outPositions[i] = y
            y += size + gap.roundToPx()
        }
        if (sizes.isNotEmpty()) {
            val last = sizes.lastIndex
            outPositions[last] = maxOf(outPositions[last], totalSize - sizes[last])
        }
    }
}

/**
 * The left menu ("rail") from the design, including records and the existing inbox.
 * [needsTrip]: greyed out until the crew's trip starts (the host's 출발): drops, games and the inbox come with it.
 */
private data class RailItem(val label: String, val icon: ImageVector, val screen: Screen, val needsTrip: Boolean = false)

private val railItems = listOf(
    RailItem("홈", CdIcons.Home, Screen.Drive),
    RailItem("드랍", CdIcons.Send, Screen.DropSend, needsTrip = true),
    RailItem("크루", CdIcons.Crew, Screen.CreateCrew),
    RailItem("게임", CdIcons.Game, Screen.Games, needsTrip = true),
    RailItem("기록", CdIcons.Trophy, Screen.Safety()),
    RailItem("편지함", CdIcons.Inbox, Screen.Inbox, needsTrip = true),
)

private fun Screen.railGroup(): Screen = when (this) {
    Screen.Drive, Screen.QuickDropEdit, Screen.FuelMe, is Screen.FuelThem, is Screen.Decided -> Screen.Drive
    Screen.DropSend, is Screen.Shout, is Screen.AiRecommend, is Screen.Vote -> Screen.DropSend
    Screen.CreateCrew, Screen.DestinationSearch, Screen.JoinCrew -> Screen.CreateCrew
    Screen.Games -> Screen.Games
    Screen.Inbox -> Screen.Inbox
    is Screen.Safety, is Screen.SafetyResult -> Screen.Safety()
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
    /** False until the crew's trip starts: the rail's 드랍, 게임 and 편지함 are greyed out until then. */
    tripStarted: Boolean = true,
    /**
     * Something app-only drawn low in the rail, just above [myCar]: the DEMO button. The rail is never under a
     * screen's own content, unlike a corner of the screen, where boards pin their buttons. Null = nothing.
     */
    railExtra: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DesignScale(designWidth = DESIGN_WIDTH + NAV_PANEL_WIDTH) {
        Row(modifier = Modifier.fillMaxSize().background(CdColors.Gray100)) {
            Box(modifier = Modifier.width(NAV_PANEL_WIDTH.dp).fillMaxHeight()) { navPanel() }
            Rail(current = current.railGroup(), onTab = onTab, myCar = myCar, tripStarted = tripStarted, extra = railExtra)
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
 * Also used by the start question (core/demo/StartModeScreen.kt), shown before this shell.
 */
@Composable
internal fun DesignScale(designWidth: Float, content: @Composable () -> Unit) {
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
private fun Rail(
    current: Screen,
    onTab: (Screen) -> Unit,
    myCar: Car?,
    tripStarted: Boolean,
    extra: (@Composable () -> Unit)?,
) {
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
            modifier = Modifier
                // The design's `margin-bottom: 14px`: with the rail's 6 between items, 20 from the logo to 홈.
                .padding(bottom = 14.dp)
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(CdColors.Blue),
            contentAlignment = Alignment.Center,
        ) {
            Text("CD", color = CdColors.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        railItems.forEach { item ->
            val on = item.screen == current
            val enabled = tripStarted || !item.needsTrip
            Column(
                modifier = Modifier
                    .size(64.dp)
                    .alpha(if (enabled) 1f else 0.4f) // like the greyed-out quick drops on board 04
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) CdColors.BlueLight else CdColors.White)
                    .clickable(enabled = enabled) { onTab(item.screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                // Icon and label centred, 4 apart (the design's `gap: 4px`).
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                val tint = if (on) CdColors.BlueDark else CdColors.Gray500
                Icon(item.icon, contentDescription = null, tint = tint)
                Text(item.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        // The rest goes to the bottom of the rail, [extra] 12 above the car (6 more than the rail's usual gap).
        if (myCar != null || extra != null) Spacer(Modifier.weight(1f))
        if (extra != null) Box(Modifier.padding(bottom = 6.dp)) { extra() }
        if (myCar != null) {
            // The car and "내 차" sit 2 apart (the design's `gap: 2px`), closer than the rail's 6.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CarBadge(myCar)
                Text("내 차", color = CdColors.Gray600, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
