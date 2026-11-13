package com.cardrop.navi

import com.cardrop.core.model.NavRoute
import com.cardrop.core.model.Place
import com.cardrop.drive.TripDecisions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Owner: Member B.
 *
 * Hands the crew's route to a navigation app installed from the Pleos app market.
 * Choose the app with `cardrop.navApp` in local.properties:
 * - `inapp` -> InAppNavigation: a real map drawn inside CarDrop (default; use on the emulator)
 * - `naver` -> NaverMapHandoff (Naver Map deep link, up to 5 waypoints, no API key)
 * - `kakao` -> TODO: Kakao Navi SDK (needs a Kakao native app key; Kakao Navi has no URL scheme)
 *
 * Two steps on purpose. Pleos allows only one navigation app to hold the active route, and
 * switching needs an explicit user choice, so:
 * - [setRoute] records what the crew agreed on and never opens another app;
 * - [launch] opens the map app. Call it only from a button the driver pressed.
 */
interface NavigationHandoff {
    val decisions: TripDecisions

    /** End the current trip without opening another app. */
    fun clear()

    /** The route the crew agreed on, and the last thing that happened to it. */
    val route: StateFlow<NavRoute>

    /** False when the chosen map app is not installed; the UI should then explain that. */
    val isAvailable: Boolean

    /** Records the route. [waypoint] = the stop the crew voted for, or null for none. */
    fun setRoute(destination: Place, waypoint: Place? = null, reason: String? = null)

    /** Opens the navigation app on the current route. Returns false if it could not open. */
    fun launch(): Boolean

    /**
     * This car stopped at the waypoint or drove past it (the stop is then off the route). Only the
     * in-app navigator knows; an external map app reports nothing.
     */
    val waypointEvents: Flow<WaypointEvent> get() = emptyFlow()

    /** This car just got to the waypoint (within 300 m), before [waypointEvents] says whether it stopped. */
    val reachedWaypoint: Flow<Place> get() = emptyFlow()
}

/** What this car did at [place], the crew's stop. */
data class WaypointEvent(val place: Place, val result: WaypointResult)
