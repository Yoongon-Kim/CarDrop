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
    /** This reading is the first within [GuidanceTracker]'s arrive distance of the waypoint (once per waypoint). */
    val atWaypoint: Boolean,
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
 * - at the waypoint = the first reading within [arriveMetres] of it (shown at once, before it is decided);
 * - waypoint ARRIVED = within [arriveMetres] of it and slower than [slowKmh] for [stopMillis];
 * - waypoint PASSED = came within [nearMetres], then got more than [passedMetres] away without
 *   arriving. The motorway passes rest areas 70-100 m from their map point, so distance alone can't
 *   tell a stop from a drive-by; slowing down can.
 *   Also PASSED (Member A, 2026-11-15): the car got within [approachMetres] of it, then [movedAwayMetres] further away
 *   than its closest. That catches a stop whose map point lies far from the road, and a stop the car was already past
 *   when it became the waypoint; before, neither was ever passed, so every reroute sent the car back to it.
 * - still parked at a stop it ARRIVED at = within [arriveMetres] of it and not faster than [slowKmh]
 *   ([isStillAt]); the navigator shows "… · 정차 중" until then.
 */
class GuidanceTracker(
    private val offRouteMetres: Double = 60.0,
    private val offRouteFixes: Int = 3,
    private val arriveMetres: Double = 300.0,
    private val slowKmh: Double = 20.0,
    private val stopMillis: Long = 10_000,
    private val nearMetres: Double = 600.0,
    private val passedMetres: Double = 800.0,
    private val approachMetres: Double = 3_000.0,
    private val movedAwayMetres: Double = 1_000.0,
) {
    private var lastKm = 0.0
    private var offCount = 0
    private var previous: GpsFix? = null

    // The waypoint being watched, and what we know about it so far.
    private var waypointId: String? = null
    private var cameNear = false
    private var wasAt = false
    private var slowSinceMillis: Long? = null
    private var decided = false
    private var closestMetres = Double.MAX_VALUE

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
        val justAt = waypoint != null && toWaypointMetres != null && justReached(waypoint, toWaypointMetres)
        return TrackUpdate(
            km = lastKm,
            offRoute = !onRoute,
            snapped = if (onRoute) route.geometry.pointAt(lastKm) else null,
            atWaypoint = justAt,
            waypoint = if (waypoint != null && toWaypointMetres != null) waypointResult(waypoint, toWaypointMetres, fix) else forgetWaypoint(),
            arrived = onRoute && route.totalKm - lastKm < 0.05,
            // Keep requesting until the navigator's cooldown allows a reroute. A one-shot
            // trigger could be lost during cooldown or when a previous request failed.
            needsReroute = !onRoute && offCount >= offRouteFixes && !nearWaypoint,
        )
    }

    /**
     * True while the car is still parked at [stop], a stop it ARRIVED at: within [arriveMetres] of it and
     * not faster than [slowKmh]. Driving off (faster, or further away) ends it.
     */
    fun isStillAt(stop: Place, fix: GpsFix): Boolean {
        val metres = haversineKm(GeoPoint(fix.lat, fix.lng), GeoPoint(stop.lat, stop.lng)) * 1000
        return metres <= arriveMetres && (fix.speedKmh ?: 0.0) <= slowKmh
    }

    /** No waypoint now: a later vote for the same place is watched afresh. */
    private fun forgetWaypoint(): WaypointResult? {
        waypointId = null
        return null
    }

    /** Starts watching [waypoint] afresh when it is a different one. */
    private fun watch(waypoint: Place) {
        if (waypoint.id == waypointId) return
        waypointId = waypoint.id
        cameNear = false
        wasAt = false
        slowSinceMillis = null
        decided = false
        closestMetres = Double.MAX_VALUE
    }

    /** True on the first reading within [arriveMetres] of [waypoint]. */
    private fun justReached(waypoint: Place, metres: Double): Boolean {
        watch(waypoint)
        if (wasAt || decided || metres > arriveMetres) return false
        wasAt = true
        return true
    }

    /** ARRIVED or PASSED once per waypoint; null until one of them happens. */
    private fun waypointResult(waypoint: Place, metres: Double, fix: GpsFix): WaypointResult? {
        watch(waypoint)
        if (decided) return null
        if (metres <= nearMetres) cameNear = true
        closestMetres = minOf(closestMetres, metres)
        val movedAway = closestMetres <= approachMetres && metres > closestMetres + movedAwayMetres
        val slow = fix.speedKmh != null && fix.speedKmh <= slowKmh
        slowSinceMillis = if (metres <= arriveMetres && slow) slowSinceMillis ?: fix.timeMillis else null
        val result = when {
            slowSinceMillis?.let { fix.timeMillis - it >= stopMillis } == true -> WaypointResult.ARRIVED
            cameNear && metres > passedMetres -> WaypointResult.PASSED
            movedAway && metres > passedMetres -> WaypointResult.PASSED
            else -> null
        }
        if (result != null) decided = true
        return result
    }
}
