package com.cardrop.drive

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A latitude / longitude pair in degrees. */
data class GeoPoint(val lat: Double, val lng: Double)

/** The arrow shown in the navigator's turn banner. */
enum class NavArrow { STRAIGHT, LEFT, RIGHT, UTURN, ARRIVE }

/**
 * One turn instruction on the route.
 * [km] = distance from the start of the route, [road] = the road after the turn (may be blank).
 */
data class NavStep(val km: Double, val arrow: NavArrow, val text: String, val road: String)

/** A point on the route plus the driving direction in degrees clockwise from north. */
data class RoutePoint(val lat: Double, val lng: Double, val bearing: Double)

/**
 * Owner: Member B.
 *
 * The demo trip on real roads: 신촌 -> 경부고속도로 -> 신갈JC -> 영동고속도로 -> 강릉 경포해변 (about 244 km).
 * The shape and the turns come from OpenStreetMap (OSRM router) and are baked into [DemoRouteData] by
 * `tools/bake_route.py`, so the demo needs no routing server.
 *
 * Used by [ScriptedRouteSimulator] (`cardrop.navApp=naver`) and by `tools/drive_gps.py`, which feeds this
 * route into the emulator's GPS so the in-app navigator has a car to guide.
 */
object DemoRoute {

    private val geometry = RouteGeometry(decodePolyline(DemoRouteData.POLYLINE))

    val points: List<GeoPoint> get() = geometry.points
    val steps: List<NavStep> = DemoRouteData.STEPS
    val totalKm: Double = geometry.totalKm

    /** Where a car is after driving [km] from the start, and which way it faces. */
    fun pointAt(km: Double): RoutePoint = geometry.pointAt(km)

    /** km from the start of the route to the route point closest to [lat], [lng]. */
    fun kmOf(lat: Double, lng: Double): Double = geometry.locate(lat, lng).km

    fun kmOf(place: Place): Double = kmOf(place.lat, place.lng)

    /** How far along the route a car is. */
    fun kmOf(position: CarPosition): Double = (totalKm - position.kmToDestination).coerceIn(0.0, totalKm)

    /** The next turn after [km], or null when nothing but the arrival is left. */
    fun nextStep(km: Double): NavStep? = steps.firstOrNull { it.km > km + 0.01 }

    /** The road a car is on at [km] (from the last named turn before it), or "" if unknown. */
    fun roadAt(km: Double): String = steps.lastOrNull { it.km <= km && it.road.isNotBlank() }?.road.orEmpty()

    /** The part of the route between two distances. */
    fun slice(fromKm: Double, toKm: Double): List<GeoPoint> = geometry.slice(fromKm, toKm)

    /**
     * Places from [pool] that every car still has ahead of it, at least [marginKm] past the car in front,
     * nearest first. A vote takes up to 2 minutes, so a stop picked now must still be ahead when it ends.
     */
    fun placesAhead(pool: List<Place>, positions: Collection<CarPosition>, marginKm: Double = 25.0): List<Place> {
        val frontKm = positions.maxOfOrNull { kmOf(it) } ?: 0.0
        return pool.map { it to kmOf(it) }
            .filter { (_, km) -> km > frontKm + marginKm }
            .sortedBy { (_, km) -> km }
            .map { (place, _) -> place }
    }
}

/** Great-circle distance in km. */
fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(h))
}

/** Direction from [a] to [b] in degrees clockwise from north (0..360). */
fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Decodes a Google encoded polyline (the format OSRM returns). */
fun decodePolyline(encoded: String, precision: Int = 5): List<GeoPoint> {
    val factor = 10.0.pow(precision)
    val result = ArrayList<GeoPoint>()
    var index = 0
    var lat = 0L
    var lng = 0L
    while (index < encoded.length) {
        for (coordinate in 0..1) {
            var shift = 0
            var value = 0L
            while (true) {
                val b = encoded[index++].code - 63
                value = value or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) break
            }
            val delta = if (value and 1L != 0L) (value shr 1).inv() else value shr 1
            if (coordinate == 0) lat += delta else lng += delta
        }
        result += GeoPoint(lat / factor, lng / factor)
    }
    return result
}
