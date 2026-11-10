package com.cardrop.navi

import android.util.Log
import com.cardrop.core.model.NavRoute
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RoutePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the navigator is doing right now. */
enum class NavStatus { WAITING_GPS, ROUTING, GUIDING, ERROR }

data class NavigatorState(
    val status: NavStatus = NavStatus.WAITING_GPS,
    /** Latest GPS reading (speed and direction filled in). */
    val fix: GpsFix? = null,
    val route: LiveRoute? = null,
    /** km along [route]. */
    val progressKm: Double = 0.0,
    /** This car's position on the route; null while off the route (then use [fix]). */
    val snapped: RoutePoint? = null,
    val offRoute: Boolean = false,
    val arrived: Boolean = false,
    val error: String? = null,
)

/**
 * Owner: Member B. `cardrop.navApp=inapp` (the default).
 *
 * A real navigator inside CarDrop, drawn by [InAppNavigatorPanel]:
 * - position: this car's GPS ([LocationSource]); on the emulator, `tools/drive_gps.py` feeds it;
 * - route: asked from [router] (Kakao, then OSRM) from where the car is to the crew destination,
 *   through the voted waypoint; asked again when the destination or waypoint changes, when the car
 *   leaves the route, and after the car reaches the waypoint;
 * - guidance: the next turn and arrival times from the live position ([NavPanelState]).
 *
 * Used on the emulator, where the built-in Pleos navigation shows no map (style `connect_empty`).
 */
class InAppNavigation(
    private val scope: CoroutineScope,
    private val location: LocationSource,
    private val router: RouteProvider,
    private val now: () -> Long = System::currentTimeMillis,
) : NavigationHandoff {

    private val _route = MutableStateFlow(NavRoute())
    override val route: StateFlow<NavRoute> = _route

    override val isAvailable: Boolean = true

    private val _guiding = MutableStateFlow(false)

    /** True once the driver pressed 출발하기 / 안내 시작: the map then follows this car. */
    val guiding: StateFlow<Boolean> = _guiding

    private val _state = MutableStateFlow(NavigatorState())
    val state: StateFlow<NavigatorState> = _state

    private val tracker = GuidanceTracker()
    private var routing: Job? = null
    private var lastRequestMillis = 0L

    init {
        location.start()
        scope.launch { location.fix.filterNotNull().collect(::onFix) }
    }

    /** Called by MainActivity once the driver allows location access. */
    fun onLocationPermissionGranted() {
        location.start()
    }

    override fun setRoute(destination: Place, waypoint: Place?, reason: String?) {
        val old = _route.value
        if (waypoint != null && isBehind(waypoint)) {
            // The vote ended after this car drove past the stop: don't send it back.
            _route.value = NavRoute(destination, waypoint = null, lastAction = "${waypoint.name}은 이미 지났어요 · 목적지로 안내해요")
            if (old.destination != destination || old.waypoint != null) requestRoute()
            return
        }
        _route.value = NavRoute(destination, waypoint, lastAction = reason)
        if (old.destination != destination || old.waypoint != waypoint || _state.value.route == null) requestRoute()
    }

    override fun launch(): Boolean {
        // Already guiding (e.g. "지금 안내 시작" after a vote): keep the message about what changed.
        if (!_guiding.value) _route.update { it.copy(lastAction = "안내를 시작했어요") }
        _guiding.value = true
        return _route.value.destination != null
    }

    /** Places from [pool] near the route and at least [marginKm] ahead, nearest first; null without a route. */
    fun placesAhead(pool: List<Place>, marginKm: Double = 25.0): List<Place>? {
        val current = _state.value
        val route = current.route ?: return null
        return pool.mapNotNull { place ->
            val located = route.geometry.locate(place.lat, place.lng)
            if (located.offMeters < 1_500 && located.km > current.progressKm + marginKm) place to located.km else null
        }.sortedBy { it.second }.map { it.first }
    }

    private fun isBehind(place: Place): Boolean {
        val current = _state.value
        val route = current.route ?: return false
        val located = route.geometry.locate(place.lat, place.lng)
        return located.offMeters < 1_500 && located.km < current.progressKm - 0.3
    }

    private fun onFix(raw: GpsFix) {
        val fix = tracker.withMotion(raw)
        val route = _state.value.route
        if (route == null) {
            _state.update { it.copy(fix = fix) }
            // No route yet: ask now, or retry every 15 s after a failure.
            val retryDue = now() - lastRequestMillis > RETRY_MILLIS
            if (_route.value.destination != null && routing?.isActive != true && (_state.value.status != NavStatus.ERROR || retryDue)) {
                requestRoute()
            }
            return
        }
        val waypoint = _route.value.waypoint
        val update = tracker.update(route, fix, waypoint)
        val justArrived = update.arrived && !_state.value.arrived
        _state.update {
            it.copy(fix = fix, progressKm = update.km, snapped = update.snapped, offRoute = update.offRoute, arrived = update.arrived)
        }
        when {
            update.reachedWaypoint && waypoint != null -> {
                _route.update { it.copy(waypoint = null, lastAction = "${waypoint.name}에 도착했어요") }
                requestRoute()
            }
            justArrived -> _route.update { it.copy(lastAction = "목적지에 도착했어요") }
            update.needsReroute && now() - lastRequestMillis > REROUTE_GAP_MILLIS -> requestRoute(isReroute = true)
        }
    }

    private fun requestRoute(isReroute: Boolean = false) {
        val destination = _route.value.destination ?: return
        val fix = _state.value.fix
        if (fix == null) {
            _state.update { it.copy(status = NavStatus.WAITING_GPS) }
            return // the first GPS reading will ask again (onFix)
        }
        val waypoint = _route.value.waypoint
        routing?.cancel()
        lastRequestMillis = now()
        _state.update { it.copy(status = NavStatus.ROUTING) }
        routing = scope.launch {
            try {
                val route = router.route(GeoPoint(fix.lat, fix.lng), fix.bearing, destination, waypoint)
                tracker.reset()
                _state.update {
                    it.copy(status = NavStatus.GUIDING, route = route, progressKm = 0.0, offRoute = false, arrived = false, error = null)
                }
                if (isReroute) _route.update { it.copy(lastAction = "경로를 벗어나서 새 경로를 찾았어요") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Route failed: ${e.message}")
                _state.update { it.copy(status = NavStatus.ERROR, error = e.message) }
            }
        }
    }

    private companion object {
        const val TAG = "CarDropNavi"
        const val RETRY_MILLIS = 15_000L
        const val REROUTE_GAP_MILLIS = 8_000L
    }
}
