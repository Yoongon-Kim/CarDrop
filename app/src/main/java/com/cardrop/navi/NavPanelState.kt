package com.cardrop.navi

import com.cardrop.core.model.NavRoute
import com.cardrop.drive.NavArrow
import com.cardrop.drive.NavStep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The dark turn banner at the top of the navigator ("1.2 km · 여주휴게소 진입"). */
data class NavBanner(val arrow: NavArrow, val distance: String, val text: String)

/** One row of the bottom card: a place and its arrival time ("16:40"). */
data class EtaRow(val name: String, val eta: String)

/** Everything the navigator panel shows besides the map. */
data class NavPanelState(
    val banner: NavBanner,
    val speedKmh: Int,
    val waypoint: EtaRow?,
    val destination: EtaRow?,
    /** Which service made the route, for the attribution line ("카카오모빌리티" / "OSRM"). */
    val provider: String?,
)

/**
 * Owner: Member B. Works out the banner, speed and arrival times from the live route and GPS.
 * Pure function (no Android), unit tested in `NavPanelStateTest`.
 */
fun navPanelState(
    route: NavRoute,
    nav: NavigatorState,
    guiding: Boolean,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): NavPanelState {
    val live = nav.route
    val km = nav.progressKm
    val banner = when {
        route.destination == null -> NavBanner(NavArrow.STRAIGHT, "", "목적지를 정하면 길을 안내해요")
        nav.fix == null -> NavBanner(NavArrow.STRAIGHT, "", "GPS 신호를 찾고 있어요")
        live == null && nav.status == NavStatus.ERROR -> NavBanner(NavArrow.STRAIGHT, "", "경로를 찾지 못했어요 · 다시 시도하는 중")
        live == null -> NavBanner(NavArrow.STRAIGHT, "", "경로를 찾고 있어요")
        nav.offRoute -> NavBanner(NavArrow.UTURN, "", "경로를 벗어났어요 · 새 경로를 찾는 중")
        nav.arrived -> NavBanner(NavArrow.ARRIVE, "", "목적지에 도착했어요")
        !guiding -> NavBanner(NavArrow.STRAIGHT, formatKm(live.totalKm - km), "출발하기를 누르면 안내를 시작해요")
        else -> {
            // The next thing to do: the next turn, or entering the waypoint if that comes first.
            val waypointStep = route.waypoint?.let { place ->
                live.waypointKm?.let { NavStep(it, NavArrow.RIGHT, "${place.name} 진입", "") }
            }?.takeIf { it.km > km }
            val next = listOfNotNull(live.nextStep(km), waypointStep).minByOrNull { it.km }
                ?: NavStep(live.totalKm, NavArrow.ARRIVE, "목적지 도착", "")
            val distance = next.km - km
            if (distance > LONG_STRETCH_KM) {
                NavBanner(NavArrow.STRAIGHT, formatKm(distance), "${live.roadAt(km).ifBlank { "도로" }} 따라 직진")
            } else {
                NavBanner(next.arrow, formatKm(distance), next.text)
            }
        }
    }

    val waypointRow = route.waypoint?.let { place ->
        val eta = live?.waypointKm?.let { clock(nowMillis, live.secondsBetween(km, it), zone) }
        EtaRow(place.name, eta.orEmpty())
    }
    val destinationRow = route.destination?.let { place ->
        val eta = when {
            nav.arrived -> "도착" // the design's last screen (632): the arrival time becomes "도착"
            live != null -> clock(nowMillis, live.secondsBetween(km, live.totalKm), zone)
            else -> ""
        }
        EtaRow(place.name, eta)
    }
    return NavPanelState(
        banner = banner,
        speedKmh = nav.fix?.speedKmh?.roundToInt() ?: 0,
        waypoint = waypointRow,
        destination = destinationRow,
        provider = live?.provider,
    )
}

/** "350 m", "4.2 km", "68 km". */
fun formatKm(km: Double): String = when {
    km < 1.0 -> "${((km * 1000) / 50).roundToInt().coerceAtLeast(1) * 50} m"
    km < 10.0 -> String.format(Locale.US, "%.1f km", km)
    else -> "${km.roundToInt()} km"
}

/** The clock time [seconds] from now, e.g. "14:32" (also board 14's "도착 예정"). */
internal fun clock(nowMillis: Long, seconds: Double, zone: ZoneId): String =
    Instant.ofEpochMilli(nowMillis + (seconds.coerceAtLeast(0.0) * 1000).toLong())
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

/** Further than this (km) to the next turn: show "… 따라 직진" instead of the turn. */
private const val LONG_STRETCH_KM = 3.0
