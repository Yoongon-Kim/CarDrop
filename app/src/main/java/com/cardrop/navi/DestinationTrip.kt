package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.haversineKm
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Owner: lead. How far the crew destination is by road from this car, and how long the drive takes:
 * board 01's "약 62km · 1시간 5분". From the navigator's live route (Kakao includes traffic), so it
 * is the real drive, not a straight line.
 */
data class DestinationTrip(val km: Double, val minutes: Int) {
    /** "약 62km · 1시간 5분" (design board 01). */
    val text: String get() = "약 ${tripKmText(km)} · ${tripTimeText(minutes)}"
}

/**
 * The rest of [state]'s live route to [destination]: from where the car is on it to its end. Null
 * when there is no route yet, or when the route ends somewhere else: the destination just changed
 * and the new route is still on its way, so the old numbers would be wrong.
 */
fun tripToDestination(state: NavigatorState, destination: Place): DestinationTrip? {
    val route = state.route ?: return null
    val end = route.geometry.points.last()
    // A route ends on the road nearest the destination, which can be a little way from its map point.
    if (haversineKm(end, GeoPoint(destination.lat, destination.lng)) > ROUTE_END_KM) return null
    val km = (route.totalKm - state.progressKm).coerceAtLeast(0.0)
    val minutes = (route.secondsBetween(state.progressKm, route.totalKm) / 60).roundToInt()
    return DestinationTrip(km, minutes)
}

/** "850m" under 1 km, "5.4km" under 10 km, then "62km". */
fun tripKmText(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt()}m"
    km < 10 -> String.format(Locale.US, "%.1fkm", km)
    else -> "${km.roundToInt()}km"
}

/** "9분", "1시간 5분", "2시간". Never "0분": a trip shorter than a minute shows "1분". */
fun tripTimeText(minutes: Int): String {
    val m = minutes.coerceAtLeast(1)
    val hours = m / 60
    val rest = m % 60
    return when {
        hours == 0 -> "${rest}분"
        rest == 0 -> "${hours}시간"
        else -> "${hours}시간 ${rest}분"
    }
}

private const val ROUTE_END_KM = 2.0
