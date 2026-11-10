package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.NavArrow
import com.cardrop.drive.NavStep
import com.cardrop.drive.RouteGeometry
import com.cardrop.drive.decodePolyline
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Owner: Member B.
 *
 * Backup routing: the public OSRM server (OpenStreetMap roads, no key, no traffic).
 * It is a free demo server with no uptime promise, so Kakao is tried first.
 * Docs: https://project-osrm.org/docs/v5.24.0/api/
 */
class OsrmDirections(private val baseUrl: String = "https://router.project-osrm.org") : RouteProvider {

    override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute {
        val coordinates = listOfNotNull(
            xy(from.lng, from.lat),
            waypoint?.let { xy(it.lng, it.lat) },
            xy(destination.lng, destination.lat),
        )
        // Only the start gets a bearing: snap to the side of the road the car is driving on.
        val bearings = listOf(heading?.let { "${it.roundToInt().mod(360)},60" }.orEmpty()) +
            List(coordinates.size - 1) { "" }
        val url = "$baseUrl/route/v1/driving/${coordinates.joinToString(";")}" +
            "?overview=full&geometries=polyline6&steps=true&continue_straight=true" +
            (if (heading != null) "&bearings=${bearings.joinToString(";")}" else "")
        return parseOsrmRoute(httpGet(url), hasWaypoint = waypoint != null)
    }

    private fun xy(x: Double, y: Double) = String.format(Locale.US, "%.6f,%.6f", x, y)
}

/** Turns OSRM's JSON into a [LiveRoute], writing the turn texts in Korean. Public for unit tests. */
fun parseOsrmRoute(json: String, hasWaypoint: Boolean): LiveRoute {
    val root = JSONObject(json)
    if (root.optString("code") != "Ok") throw IOException("OSRM ${root.optString("code")}: ${root.optString("message")}")
    val route = root.getJSONArray("routes").getJSONObject(0)
    val geometry = RouteGeometry(decodePolyline(route.getString("geometry"), precision = 6))

    val steps = ArrayList<NavStep>()
    val timeline = ArrayList<Pair<Double, Double>>()
    var metres = 0.0
    var seconds = 0.0
    var lastKm = 0.0
    val legs = route.getJSONArray("legs")
    for (l in 0 until legs.length()) {
        val legSteps = legs.getJSONObject(l).getJSONArray("steps")
        for (s in 0 until legSteps.length()) {
            val step = legSteps.getJSONObject(s)
            metres += step.optDouble("distance", 0.0)
            seconds += step.optDouble("duration", 0.0)
            timeline += (metres / 1000.0).coerceAtMost(geometry.totalKm) to seconds

            val maneuver = step.getJSONObject("maneuver")
            val type = maneuver.optString("type")
            val modifier = maneuver.optString("modifier", "straight")
            val isLastLeg = l == legs.length() - 1
            if (type == "depart" || (type == "arrive" && !isLastLeg)) continue
            if (type == "new name" || (type == "continue" && modifier == "straight")) continue
            val location = maneuver.getJSONArray("location")
            val located = geometry.locate(location.getDouble(1), location.getDouble(0), fromKm = lastKm - 0.05)
            lastKm = located.km
            val (arrow, text) = osrmKorean(type, modifier, step.optString("name"), step.optString("destinations"))
            steps += NavStep(located.km, arrow, text, step.optString("name").substringBefore(';').trim())
        }
    }
    val waypointKm = if (hasWaypoint && legs.length() > 1) legs.getJSONObject(0).optDouble("distance") / 1000.0 else null
    val startRoad = legs.getJSONObject(0).getJSONArray("steps").optJSONObject(0)?.optString("name")?.substringBefore(';')?.trim().orEmpty()
    return LiveRoute(geometry, steps, timeline, waypointKm, "OSRM", startRoad)
}

/** Korean instruction for an OSRM maneuver (same rules as tools/bake_route.py). */
internal fun osrmKorean(type: String, modifier: String, rawName: String, rawDestinations: String): Pair<NavArrow, String> {
    val name = rawName.substringBefore(';').trim()
    val destinations = rawDestinations.substringAfter(':', rawDestinations).split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val destination = listOf("강릉", "원주", "영동", "동해").firstNotNullOfOrNull { key -> destinations.firstOrNull { key in it } }
        ?: destinations.firstOrNull().orEmpty()
    val left = "left" in modifier
    val right = "right" in modifier
    val side = if (left) "왼쪽" else if (right) "오른쪽" else ""
    val turnArrow = if (left) NavArrow.LEFT else NavArrow.RIGHT
    val target = destination.ifEmpty { name }
    return when {
        type == "arrive" -> NavArrow.ARRIVE to "목적지 도착"
        type == "on ramp" -> turnArrow to if (target.isNotEmpty()) "$target 방면 진입" else "$side 진입로"
        type == "off ramp" -> turnArrow to if (target.isNotEmpty()) "$target 방면 출구" else "$side 출구"
        type == "merge" -> NavArrow.STRAIGHT to if (name.ifEmpty { destination }.isNotEmpty()) "${name.ifEmpty { destination }} 합류" else "본선 합류"
        type == "fork" -> turnArrow to if (target.isNotEmpty()) "$target 방면 $side" else "$side 방향"
        "uturn" in modifier -> NavArrow.UTURN to "$name 유턴".trim()
        left -> NavArrow.LEFT to "$name 좌회전".trim()
        right -> NavArrow.RIGHT to "$name 우회전".trim()
        else -> NavArrow.STRAIGHT to if (name.isNotEmpty()) "$name 방면 직진" else "직진"
    }
}
