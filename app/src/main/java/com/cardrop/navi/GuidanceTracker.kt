package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RoutePoint
import com.cardrop.drive.bearingDegrees
import com.cardrop.drive.haversineKm

/** What one GPS reading means for the current route. */
data class TrackUpdate(
    /** km along the route. */
    val km: Double,
    val offRoute: Boolean,
    /** The GPS point moved onto the route ("map matching"); null while off the route. */
    val snapped: RoutePoint?,
    /** This reading decided what happened at the waypoint (once per waypoint); null = nothing yet. */
    val waypoint: WaypointResult?,
    val arrived: Boolean,
    /** True after enough off-route fixes, until a new route arrives or the car rejoins it. */
    val needsReroute: Boolean,
)

/** What this car did at the crew's stop (the waypoint). */
enum class WaypointResult {
    /** Stopped there. */
    ARRIVED,

    /** Came close, then drove on without stopping. */
    PASSED,
}

/**
 * Owner: Member B. The navigator's core rules, with no Android code so they are unit tested
 * (`GuidanceTrackerTest`):
 * - progress = the nearest route point, searched a little behind and ahead of the last one;
 * - off route = more than [offRouteMetres] away for [offRouteFixes] readings in a row -> reroute;
 * - waypoint ARRIVED = within [arriveMetres] of it and slower than [slowKmh] for [stopMillis];
 * - waypoint PASSED = came within [nearMetres], then got more than [passedMetres] away without
 *   arriving. The motorway passes rest areas 70-100 m from their map point, so distance alone can't
 *   tell a stop from a drive-by; slowing down can.
 */
class GuidanceTracker(
    private val offRouteMetres: Double = 60.0,
    private val offRouteFixes: Int = 3,
    private val arriveMetres: Double = 300.0,
    private val slowKmh: Double = 20.0,
    private val stopMillis: Long = 10_000,
    private val nearMetres: Double = 600.0,
    private val passedMetres: Double = 800.0,
) {
    private var lastKm = 0.0
    private var offCount = 0
    private var previous: GpsFix? = null

    // The waypoint being watched, and what we know about it so far.
    private var waypointId: String? = null
    private var cameNear = false
    private var slowSinceMillis: Long? = null
    private var decided = false

    /** Call when a new route arrives. */
    fun reset() {
        lastKm = 0.0
        offCount = 0
    }

    /** Fills in speed and direction from the previous reading when the GPS did not report them. */
    fun withMotion(fix: GpsFix): GpsFix {
        val before = previous
        previous = fix
        if (before == null || (fix.speedKmh != null && fix.bearing != null)) return fix
        val km = haversineKm(GeoPoint(before.lat, before.lng), GeoPoint(fix.lat, fix.lng))
        val hours = (fix.timeMillis - before.timeMillis) / 3_600_000.0
        val moved = km > 0.005
        return fix.copy(
            speedKmh = fix.speedKmh ?: if (hours > 0 && moved) km / hours else 0.0,
            bearing = fix.bearing ?: if (moved) bearingDegrees(GeoPoint(before.lat, before.lng), GeoPoint(fix.lat, fix.lng)) else before.bearing,
        )
    }

    fun update(route: LiveRoute, fix: GpsFix, waypoint: Place?): TrackUpdate {
        val located = route.geometry.locate(fix.lat, fix.lng, fromKm = lastKm - 0.3, toKm = lastKm + 8.0)
        val onRoute = located.offMeters <= offRouteMetres
        if (onRoute) {
            lastKm = located.km
            offCount = 0
        } else {
            offCount++
        }
        val toWaypointMetres = waypoint?.let { haversineKm(GeoPoint(fix.lat, fix.lng), GeoPoint(it.lat, it.lng)) * 1000 }
        // Near the waypoint the car may skip the rest-area ramp; that is not "lost", so no reroute.
        val nearWaypoint = toWaypointMetres != null && toWaypointMetres <= 2_000
        return TrackUpdate(
            km = lastKm,
            offRoute = !onRoute,
            snapped = if (onRoute) route.geometry.pointAt(lastKm) else null,
            waypoint = if (waypoint != null && toWaypointMetres != null) waypointResult(waypoint, toWaypointMetres, fix) else forgetWaypoint(),
            arrived = onRoute && route.totalKm - lastKm < 0.05,
            // Keep requesting until the navigator's cooldown allows a reroute. A one-shot
            // trigger could be lost during cooldown or when a previous request failed.
            needsReroute = !onRoute && offCount >= offRouteFixes && !nearWaypoint,
        )
    }

    /** No waypoint now: a later vote for the same place is watched afresh. */
    private fun forgetWaypoint(): WaypointResult? {
        waypointId = null
        return null
    }

    /** ARRIVED or PASSED once per waypoint; null until one of them happens. */
    private fun waypointResult(waypoint: Place, metres: Double, fix: GpsFix): WaypointResult? {
        if (waypoint.id != waypointId) {
            waypointId = waypoint.id
            cameNear = false
            slowSinceMillis = null
            decided = false
        }
        if (decided) return null
        if (metres <= nearMetres) cameNear = true
        val slow = fix.speedKmh != null && fix.speedKmh <= slowKmh
        slowSinceMillis = if (metres <= arriveMetres && slow) slowSinceMillis ?: fix.timeMillis else null
        val result = when {
            slowSinceMillis?.let { fix.timeMillis - it >= stopMillis } == true -> WaypointResult.ARRIVED
            cameNear && metres > passedMetres -> WaypointResult.PASSED
            else -> null
        }
        if (result != null) decided = true
        return result
    }
}
