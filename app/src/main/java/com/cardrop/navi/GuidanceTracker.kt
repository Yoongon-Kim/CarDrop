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
    val reachedWaypoint: Boolean,
    val arrived: Boolean,
    /** True once per off-route episode: ask for a new route from here. */
    val needsReroute: Boolean,
)

/**
 * Owner: Member B. The navigator's core rules, with no Android code so they are unit tested
 * (`GuidanceTrackerTest`):
 * - progress = the nearest route point, searched a little behind and ahead of the last one;
 * - off route = more than [offRouteMetres] away for [offRouteFixes] readings in a row -> reroute;
 * - waypoint reached = within [waypointRadiusMetres] of it (rest areas sit beside the motorway).
 */
class GuidanceTracker(
    private val offRouteMetres: Double = 60.0,
    private val offRouteFixes: Int = 3,
    private val waypointRadiusMetres: Double = 600.0,
) {
    private var lastKm = 0.0
    private var offCount = 0
    private var previous: GpsFix? = null

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
        val reachedWaypoint = toWaypointMetres != null && toWaypointMetres <= waypointRadiusMetres
        // Near the waypoint the car may skip the rest-area ramp; that is not "lost", so no reroute.
        val nearWaypoint = toWaypointMetres != null && toWaypointMetres <= 2_000
        return TrackUpdate(
            km = lastKm,
            offRoute = !onRoute,
            snapped = if (onRoute) route.geometry.pointAt(lastKm) else null,
            reachedWaypoint = reachedWaypoint,
            arrived = onRoute && route.totalKm - lastKm < 0.05,
            needsReroute = !onRoute && offCount == offRouteFixes && !nearWaypoint,
        )
    }
}
