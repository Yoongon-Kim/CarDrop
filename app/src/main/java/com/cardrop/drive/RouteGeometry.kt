package com.cardrop.drive

import kotlin.math.cos
import kotlin.math.sqrt

/** Where a point is relative to a route: [km] along it, and how far off it in metres. */
data class Located(val km: Double, val offMeters: Double)

/**
 * Owner: Member B.
 *
 * A route's shape as a line of points, with distances along it. Used for the baked demo route
 * ([DemoRoute]) and for every live route from Kakao / OSRM (`navi/LiveRoute.kt`).
 */
class RouteGeometry(val points: List<GeoPoint>) {

    init {
        require(points.size >= 2) { "A route needs at least 2 points" }
    }

    /** cumulativeKm[i] = km from the start to points[i]. */
    private val cumulativeKm: DoubleArray = DoubleArray(points.size).also { km ->
        for (i in 1 until points.size) km[i] = km[i - 1] + haversineKm(points[i - 1], points[i])
    }

    val totalKm: Double = cumulativeKm.last()

    fun kmAtIndex(index: Int): Double = cumulativeKm[index.coerceIn(0, points.size - 1)]

    /** The point after [km] from the start, and the driving direction there. */
    fun pointAt(km: Double): RoutePoint {
        val d = km.coerceIn(0.0, totalKm)
        val i = segmentIndex(d)
        val a = points[i]
        val b = points[i + 1]
        val length = cumulativeKm[i + 1] - cumulativeKm[i]
        val t = if (length <= 0.0) 0.0 else (d - cumulativeKm[i]) / length
        return RoutePoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t, bearingDegrees(a, b))
    }

    /**
     * The route point nearest to [lat], [lng]. Searching only between [fromKm] and [toKm] keeps a car
     * from jumping to another part of the route that passes nearby.
     */
    fun locate(lat: Double, lng: Double, fromKm: Double = 0.0, toKm: Double = totalKm): Located {
        // Flat projection (degrees, longitude scaled by cos(lat)): accurate to a few metres here.
        val cosLat = cos(Math.toRadians(lat))
        val first = segmentIndex(fromKm.coerceIn(0.0, totalKm))
        val last = segmentIndex(toKm.coerceIn(0.0, totalKm))
        var bestKm = cumulativeKm[first]
        var bestSquared = Double.MAX_VALUE
        for (i in first..last) {
            val ax = points[i].lng * cosLat
            val ay = points[i].lat
            val dx = points[i + 1].lng * cosLat - ax
            val dy = points[i + 1].lat - ay
            val px = lng * cosLat - ax
            val py = lat - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val ex = px - t * dx
            val ey = py - t * dy
            val squared = ex * ex + ey * ey
            if (squared < bestSquared) {
                bestSquared = squared
                bestKm = cumulativeKm[i] + t * (cumulativeKm[i + 1] - cumulativeKm[i])
            }
        }
        return Located(bestKm, sqrt(bestSquared) * METRES_PER_DEGREE)
    }

    /** The part of the route between two distances, for drawing the driven and the remaining parts. */
    fun slice(fromKm: Double, toKm: Double): List<GeoPoint> {
        val from = fromKm.coerceIn(0.0, totalKm)
        val to = toKm.coerceIn(from, totalKm)
        val start = pointAt(from)
        val end = pointAt(to)
        val inner = points.indices.filter { cumulativeKm[it] > from && cumulativeKm[it] < to }.map { points[it] }
        return listOf(GeoPoint(start.lat, start.lng)) + inner + GeoPoint(end.lat, end.lng)
    }

    private fun segmentIndex(km: Double): Int {
        var low = 0
        var high = points.size - 1
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (cumulativeKm[mid] <= km) low = mid else high = mid
        }
        return low
    }

    private companion object {
        const val METRES_PER_DEGREE = 111_320.0
    }
}
