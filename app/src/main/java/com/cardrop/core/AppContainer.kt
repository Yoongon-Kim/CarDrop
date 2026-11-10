package com.cardrop.core

import android.content.Context
import com.cardrop.BuildConfig
import com.cardrop.ai.AiService
import com.cardrop.ai.FakeAiService
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.core.model.Place
import com.cardrop.drive.DemoRoute
import com.cardrop.drive.RouteSimulator
import com.cardrop.drive.ScriptedRouteSimulator
import com.cardrop.drive.TripCoordinator
import com.cardrop.navi.AndroidLocationSource
import com.cardrop.navi.FallbackRouteProvider
import com.cardrop.navi.GpsCrewPositions
import com.cardrop.navi.InAppNavigation
import com.cardrop.navi.KakaoDirections
import com.cardrop.navi.OsrmDirections
import com.cardrop.navi.NavigationHandoff
import com.cardrop.navi.NaverMapHandoff
import com.cardrop.vehicle.FakeVehicleRepository
import com.cardrop.vehicle.PleosVehicleRepository
import com.cardrop.vehicle.VehicleRepository
import com.cardrop.voice.FakeVoiceService
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owner: lead. The one place that decides fake vs real for every interface.
 *
 * To plug in a real implementation, change only its line here (usually behind a
 * local.properties flag). Screens never create services themselves.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Member A
    val crew: CrewRepository = FakeCrewRepository(appScope)

    // Member B
    val vehicle: VehicleRepository =
        if (BuildConfig.USE_REAL_VEHICLE) PleosVehicleRepository(context) else FakeVehicleRepository()
    /**
     * `inapp` (default): a real navigator inside CarDrop - GPS + live routes (Kakao, then OSRM).
     * `naver`: hand the route to Naver Map; car positions then come from the scripted simulator.
     */
    val inAppNavigation: InAppNavigation? =
        if (BuildConfig.NAV_APP == "naver") {
            null
        } else {
            InAppNavigation(
                scope = appScope,
                location = AndroidLocationSource(context.applicationContext),
                router = FallbackRouteProvider(
                    listOfNotNull(
                        BuildConfig.KAKAO_REST_KEY.takeIf { it.isNotBlank() }?.let(::KakaoDirections),
                        OsrmDirections(),
                    ),
                ),
            )
        }
    val route: RouteSimulator = inAppNavigation?.let { GpsCrewPositions(appScope, it, crew.myCarId) }
        ?: ScriptedRouteSimulator(
            scope = appScope,
            // This car stands still in P; the bot cars always drive.
            isParked = { carId -> carId == crew.myCarId && vehicle.snapshot.value.isParked },
        )
    // TODO(Member B): "kakao" -> KakaoNaviHandoff (Kakao Navi SDK, see CLAUDE.md)
    val navigation: NavigationHandoff = inAppNavigation ?: NaverMapHandoff(context.applicationContext)

    /** Rest stops / stations still ahead of every car: what the AI and the DEMO panel may offer. */
    fun stopsAhead(pool: List<Place>): List<Place> =
        inAppNavigation?.placesAhead(pool) ?: DemoRoute.placesAhead(pool, route.positions.value.values)

    // Lead
    val ai: AiService = FakeAiService(stopsAhead = ::stopsAhead)
    val voice: VoiceService = FakeVoiceService()

    private val trip = TripCoordinator(appScope, crew, route, navigation, voice)

    init {
        trip.start()
    }
}
