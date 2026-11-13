package com.cardrop.navi

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteSimulator
import com.cardrop.drive.haversineKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Owner: Member B. Crew positions with `cardrop.navApp=inapp`.
 *
 * This car = its GPS and live route ([InAppNavigation]). The bot cars (until Member A's real crew
 * sync) follow this car's GPS trail [BOT_GAPS_KM] behind, so they stay with the convoy whatever
 * route is driven.
 */
class GpsCrewPositions(
    scope: CoroutineScope,
    navigation: InAppNavigation,
    /** Asked on every GPS fix: with the real crew the car is picked when joining (board 03), after this starts. */
    private val myCarId: () -> String,
) : RouteSimulator {

    private val carIds = MutableStateFlow<List<String>>(emptyList())

    /** This car's GPS points, oldest first. */
    private val trail = ArrayList<GeoPoint>()

    override val positions: StateFlow<Map<String, CarPosition>> =
        combine(navigation.state, carIds) { state, ids -> build(state, ids) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun start(carIds: List<String>, destination: Place) {
        this.carIds.value = carIds
    }

    override fun stop() {
        carIds.value = emptyList()
        trail.clear()
    }

    private fun build(state: NavigatorState, ids: List<String>): Map<String, CarPosition> {
        val fix = state.fix ?: return emptyMap()
        val me = myCarId()
        if (me !in ids) return emptyMap()
        val here = state.snapped?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(fix.lat, fix.lng)
        if (trail.isEmpty() || haversineKm(trail.last(), here) > 0.02) trail += here
        if (trail.size > MAX_TRAIL) trail.subList(0, trail.size - MAX_TRAIL).clear()

        val route = state.route
        val myKmLeft = route?.let { (it.totalKm - state.progressKm).coerceAtLeast(0.0) } ?: 0.0
        val myMinutes = route?.let { it.secondsBetween(state.progressKm, it.totalKm) / 60 } ?: 0.0
        val result = mutableMapOf(me to CarPosition(me, here.lat, here.lng, myKmLeft.toFloat(), myMinutes.toInt()))
        ids.filter { it != me }.forEachIndexed { i, id ->
            val (point, behindKm) = behind(BOT_GAPS_KM.getOrElse(i) { 1.0 + i })
            val minutes = myMinutes + behindKm / 85.0 * 60
            result[id] = CarPosition(id, point.lat, point.lng, (myKmLeft + behindKm).toFloat(), minutes.toInt())
        }
        return result
    }

    /** The trail point [km] behind this car (or the trail start), and how far back it really is. */
    private fun behind(km: Double): Pair<GeoPoint, Double> {
        var left = km
        for (i in trail.size - 1 downTo 1) {
            val step = haversineKm(trail[i - 1], trail[i])
            if (step >= left) {
                val t = left / step
                val a = trail[i]
                val b = trail[i - 1]
                return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t) to km
            }
            left -= step
        }
        return (trail.firstOrNull() ?: GeoPoint(0.0, 0.0)) to (km - left)
    }

    private companion object {
        /** 삼촌, 민지, 이모 drive this far behind. */
        val BOT_GAPS_KM = listOf(0.8, 1.8, 3.0)
        const val MAX_TRAIL = 3_000
    }
}
