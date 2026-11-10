package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.NavStep
import com.cardrop.drive.RouteGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owner: Member B.
 *
 * A route from a routing service (Kakao or OSRM), from where the car was when it asked.
 * [steps] and [waypointKm] are km along this route; [timeline] holds (km, seconds from the start)
 * points so arrival times follow the road speeds the service reported (Kakao includes traffic).
 */
data class LiveRoute(
    val geometry: RouteGeometry,
    val steps: List<NavStep>,
    val timeline: List<Pair<Double, Double>>,
    val waypointKm: Double?,
    /** Shown in the navigator's attribution line, e.g. "카카오모빌리티". */
    val provider: String,
    /** The road the route starts on (before the first turn). */
    val startRoad: String = "",
) {
    val totalKm: Double get() = geometry.totalKm

    /** Seconds from the start of the route to [km]. */
    fun secondsAt(km: Double): Double {
        if (timeline.isEmpty()) return km / FALLBACK_KMH * 3600
        var previous = 0.0 to 0.0
        for (point in timeline) {
            if (km <= point.first) {
                val span = point.first - previous.first
                val t = if (span <= 0) 1.0 else (km - previous.first) / span
                return previous.second + (point.second - previous.second) * t
            }
            previous = point
        }
        return previous.second
    }

    fun secondsBetween(fromKm: Double, toKm: Double): Double = (secondsAt(toKm) - secondsAt(fromKm)).coerceAtLeast(0.0)

    /** The next turn after [km]. */
    fun nextStep(km: Double): NavStep? = steps.firstOrNull { it.km > km + 0.01 }

    /** The road at [km], from the last named turn before it. */
    fun roadAt(km: Double): String = steps.lastOrNull { it.km <= km && it.road.isNotBlank() }?.road ?: startRoad

    private companion object {
        const val FALLBACK_KMH = 80.0
    }
}

/** A routing service. Throws when it can't find a route. */
interface RouteProvider {
    /** [heading] = the car's direction (degrees), so the route starts the way the car is facing. */
    suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute
}

/** Tries each provider in order (Kakao first, then OSRM) and returns the first route found. */
class FallbackRouteProvider(private val providers: List<RouteProvider>) : RouteProvider {
    override suspend fun route(from: GeoPoint, heading: Double?, destination: Place, waypoint: Place?): LiveRoute {
        val errors = mutableListOf<String>()
        for (provider in providers) {
            try {
                return provider.route(from, heading, destination, waypoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${provider.javaClass.simpleName}: ${e.message}"
            }
        }
        throw IOException(errors.joinToString(" / ").ifEmpty { "No routing service configured" })
    }
}

/** Plain HTTP GET (no extra library). Runs on the IO thread; throws on a non-2xx answer. */
internal suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "CarDrop/0.1 (Pleos contest demo)")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code ${body.take(200)}")
        body
    } finally {
        connection.disconnect()
    }
}
