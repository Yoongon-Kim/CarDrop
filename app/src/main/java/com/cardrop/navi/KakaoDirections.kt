package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.NavArrow
import com.cardrop.drive.NavStep
import com.cardrop.drive.RouteGeometry
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Owner: Member B.
 *
 * Kakao Mobility car directions (Korean road data, live traffic, Korean turn guidance).
 * Key: a Kakao Developers app's REST API key in local.properties (`cardrop.kakaoRestKey`).
 * Free for non-commercial use up to 10,000 requests a day.
 * Docs: https://developers.kakaomobility.com/guide/navi-api/directions.html
 */
class KakaoDirections(private val restApiKey: String) : RouteProvider {

    override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute {
        if (restApiKey.isBlank()) throw IOException("no Kakao REST API key (cardrop.kakaoRestKey)")
        val origin = xy(from.lng, from.lat) + (heading?.let { ",angle=${it.roundToInt().mod(360)}" } ?: "")
        val url = buildString {
            append("https://apis-navi.kakaomobility.com/v1/directions")
            append("?origin=").append(origin)
            append("&destination=").append(xy(destination.lng, destination.lat))
            waypoint?.let { append("&waypoints=").append(xy(it.lng, it.lat)) }
            append("&priority=RECOMMEND&summary=false&alternatives=false&road_details=false")
        }
        val body = httpGet(url, mapOf("Authorization" to "KakaoAK $restApiKey"))
        return parseKakaoRoute(body, hasWaypoint = waypoint != null)
    }

    private fun xy(x: Double, y: Double) = String.format(Locale.US, "%.6f,%.6f", x, y)
}

/** Turns Kakao's JSON into a [LiveRoute]. Public for unit tests. */
fun parseKakaoRoute(json: String, hasWaypoint: Boolean): LiveRoute {
    val route = JSONObject(json).getJSONArray("routes").getJSONObject(0)
    val code = route.getInt("result_code")
    if (code != 0) throw IOException("Kakao result_code $code: ${route.optString("result_msg")}")

    val points = ArrayList<GeoPoint>()
    // For each road: the index of its last point and the seconds driven by its end.
    val roadEnds = ArrayList<Pair<Int, Double>>()
    var waypointPointIndex: Int? = null
    var seconds = 0.0
    // Guides (turns) with the section they belong to, and that section's road names.
    val guides = ArrayList<Triple<JSONObject, Int, List<String>>>()

    val sections = route.getJSONArray("sections")
    for (s in 0 until sections.length()) {
        val section = sections.getJSONObject(s)
        val roads = section.getJSONArray("roads")
        val roadNames = ArrayList<String>()
        for (r in 0 until roads.length()) {
            val road = roads.getJSONObject(r)
            roadNames += road.optString("name")
            val vertexes = road.getJSONArray("vertexes")
            var v = 0
            while (v + 1 < vertexes.length()) {
                val point = GeoPoint(lat = vertexes.getDouble(v + 1), lng = vertexes.getDouble(v))
                if (points.lastOrNull() != point) points += point
                v += 2
            }
            seconds += road.optDouble("duration", 0.0)
            roadEnds += (points.size - 1) to seconds
        }
        if (s == 0 && hasWaypoint && sections.length() > 1) waypointPointIndex = points.size - 1
        val sectionGuides = section.optJSONArray("guides")
        if (sectionGuides != null) {
            for (g in 0 until sectionGuides.length()) guides += Triple(sectionGuides.getJSONObject(g), s, roadNames)
        }
    }

    val geometry = RouteGeometry(points)
    val steps = ArrayList<NavStep>()
    var lastKm = 0.0
    for ((guide, _, roadNames) in guides) {
        val type = guide.optInt("type")
        if (type == START || type == WAYPOINT) continue // the waypoint is handled through waypointKm
        val located = geometry.locate(guide.getDouble("y"), guide.getDouble("x"), fromKm = lastKm - 0.05)
        lastKm = located.km
        val name = guide.optString("name").trim()
        val guidance = guide.optString("guidance").trim()
        val text = when {
            type == DESTINATION -> "목적지 도착"
            name.isEmpty() || name == guidance -> guidance
            else -> "$name $guidance"
        }.ifEmpty { "직진" }
        val road = roadNames.getOrNull(guide.optInt("road_index", -1)).orEmpty()
        steps += NavStep(located.km, kakaoArrow(type), text, road)
    }
    val timeline = roadEnds.map { (index, sec) -> geometry.kmAtIndex(index) to sec }
    val startRoad = sections.getJSONObject(0).getJSONArray("roads").optJSONObject(0)?.optString("name").orEmpty()
    return LiveRoute(geometry, steps, timeline, waypointPointIndex?.let(geometry::kmAtIndex), "카카오모빌리티", startRoad)
}

/** Kakao guide type -> banner arrow (type table: developers.kakaomobility.com, navi-api reference). */
internal fun kakaoArrow(type: Int): NavArrow = when (type) {
    1, 5, 8, 11, 43, 46, 48, in 24..28, in 36..40, in 76..80 -> NavArrow.LEFT
    2, 6, 7, 9, 10, 12, 42, 44, 45, 47, 49, in 18..22, in 30..34, in 70..74 -> NavArrow.RIGHT
    3, 23, 35, 75 -> NavArrow.UTURN
    DESTINATION -> NavArrow.ARRIVE
    else -> NavArrow.STRAIGHT
}

private const val START = 100
private const val DESTINATION = 101
private const val WAYPOINT = 1000
