package com.cardrop.core

import android.content.Context
import com.cardrop.BuildConfig
import com.cardrop.ai.AiService
import com.cardrop.ai.ClaudeAiService
import com.cardrop.ai.FakeAiService
import com.cardrop.ai.FallbackAiService
import com.cardrop.ai.PhraseStore
import com.cardrop.ai.PrefsPhraseStore
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.crew.FirebaseCrewRepository
import com.cardrop.core.demo.DemoCar
import com.cardrop.core.model.Place
import com.cardrop.core.navigation.AppNavigator
import com.cardrop.core.navigation.DropRoutingMemory
import com.cardrop.drive.FirebaseTrip
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteSimulator
import com.cardrop.drive.StopNotice
import com.cardrop.drive.TripCoordinator
import com.cardrop.drive.TripStart
import com.cardrop.drive.PrefsQuickDropStore
import com.cardrop.drive.QuickDrops
import com.cardrop.navi.AndroidLocationSource
import com.cardrop.navi.DestinationTrip
import com.cardrop.navi.FallbackRouteProvider
import com.cardrop.navi.tripToDestination
import com.cardrop.navi.GpsCrewPositions
import com.cardrop.navi.InAppNavigation
import com.cardrop.navi.KakaoDirections
import com.cardrop.navi.KakaoPlaceSearch
import com.cardrop.navi.PlaceSearch
import com.cardrop.navi.OsrmDirections
import com.cardrop.navi.NavigationHandoff
import com.cardrop.vehicle.FakeVehicleRepository
import com.cardrop.vehicle.PleosVehicleRepository
import com.cardrop.vehicle.VehicleRepository
import com.cardrop.voice.FakeVoiceService
import com.cardrop.voice.PleosVoiceService
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Owner: lead. The one place that decides fake vs real for every interface.
 *
 * To plug in a real implementation, change only its line here (usually behind a
 * local.properties flag). Screens never create services themselves.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Member A
    /** Which car this device drives in the real crew: the DEMO panel's "내 차", else `cardrop.myCar`. */
    val myCar: String = DemoCar.current(context)
    /**
     * `cardrop.useRealCrew=true`: the real crew on Firebase, shared by several devices; this device is
     * [myCar]. Otherwise the fake crew with bot cars (also the way back if the network fails).
     */
    val crew: CrewRepository =
        if (BuildConfig.USE_REAL_CREW) FirebaseCrewRepository(appScope, FirebaseCrewRepository.carFor(myCar))
        else FakeCrewRepository(appScope)

    // Lead
    /** Real crew: the host's 출발 and every car's position, shared through Firebase (drive/FirebaseTrip.kt). */
    private val firebaseTrip: FirebaseTrip? = if (BuildConfig.USE_REAL_CREW) FirebaseTrip(appScope, crew) else null
    /** With the real crew the cars wait for the host's 출발; the bot crew never waits. */
    val tripStart: TripStart = firebaseTrip ?: TripStart.AlwaysStarted

    // Member B
    val vehicle: VehicleRepository =
        if (BuildConfig.USE_REAL_VEHICLE) PleosVehicleRepository(context) else FakeVehicleRepository()
    /** The navigator inside CarDrop (the left third): GPS + live routes (Kakao, then OSRM). */
    val inAppNavigation: InAppNavigation =
        InAppNavigation(
            scope = appScope,
            location = AndroidLocationSource(context.applicationContext),
            router = FallbackRouteProvider(
                listOfNotNull(
                    BuildConfig.KAKAO_REST_KEY.takeIf { it.isNotBlank() }?.let(::KakaoDirections),
                    OsrmDirections(),
                ),
            ),
            kakaoMapKey = BuildConfig.KAKAO_NATIVE_KEY.takeIf { it.isNotBlank() },
        )
    /** Every car's position: this car from GPS, the others from their devices (real crew) or behind it (bots). */
    val route: RouteSimulator = GpsCrewPositions(appScope, inAppNavigation, myCarId = { crew.myCarId }, shared = firebaseTrip?.positions)
    val navigation: NavigationHandoff = inAppNavigation

    /**
     * Board 01's "약 62km · 1시간 5분": road distance and drive time from this car to the crew destination, on
     * the navigator's live route. Null until that route is there.
     */
    val destinationTrip: StateFlow<DestinationTrip?> =
        combine(inAppNavigation.state, crew.crew) { state, current -> current?.destination?.let { tripToDestination(state, it) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)

    /**
     * Where this car is: the navigator's GPS fix (there before departure too). Null before the first fix.
     * Board 02 and 10 search near it.
     */
    fun myPosition(): GeoPoint? = inAppNavigation.state.value.fix?.let { GeoPoint(it.lat, it.lng) }

    /**
     * Rest stops / stations still ahead of every car: what the AI and the DEMO panel may offer.
     * Empty until the navigator has a route (the callers then offer the pool's last places).
     */
    fun stopsAhead(pool: List<Place>): List<Place> = inAppNavigation.placesAhead(pool).orEmpty()

    // Lead
    /** Claude when local.properties has `cardrop.claudeApiKey`; the fake without one, or when a Claude call fails. */
    val ai: AiService = FakeAiService(stopsAhead = ::stopsAhead).let { fake ->
        if (BuildConfig.CLAUDE_API_KEY.isBlank()) {
            fake
        } else {
            FallbackAiService(primary = ClaudeAiService(BuildConfig.CLAUDE_API_KEY, stopsAhead = ::stopsAhead), backup = fake)
        }
    }
    /**
     * `cardrop.useRealVoice=true`: the Pleos speech SDKs (spoken Korean, real listening). Otherwise the fake:
     * captions only, and the mic always hears "배고파" (good for rehearsing without speaking).
     */
    val voice: VoiceService = if (BuildConfig.USE_REAL_VOICE) PleosVoiceService(context) else FakeVoiceService()
    /**
     * Board 02's and 10's place search while typing: Kakao Local (any place in Korea) with `cardrop.kakaoRestKey`.
     * Without the key, null: the driver can add only the places the app knows (rest stops, cafés, destinations).
     */
    val placeSearch: PlaceSearch? = BuildConfig.KAKAO_REST_KEY.takeIf { it.isNotBlank() }?.let(::KakaoPlaceSearch)
    /** Board 09's phrases, kept on this device. */
    val phrases: PhraseStore = PrefsPhraseStore(context)
    /** Board 04's quick-drop slots (퀵 드랍 편집), kept on this device. */
    val quickDrops = QuickDrops(PrefsQuickDropStore(context))

    /**
     * The open screen with its back stack, and which drops have already been opened. They live here, as long
     * as the crew and trip data, so a new activity (day/night theme, language, a second MainActivity on top)
     * keeps the driver on the same screen and doesn't open old drops again.
     */
    val navigator = AppNavigator(start = DemoCar.startScreen(BuildConfig.USE_REAL_CREW, myCar))
    val dropRouting = DropRoutingMemory({ crew.myCarId }, navigation.decisions::isSkipped)

    private val trip = TripCoordinator(
        appScope, crew, route, navigation, voice,
        tripStart = tripStart,
        autoStartsGuidance = true,
    )

    /** This car got to the crew's stop, stopped there or drove past it: the card on the CarDrop side. */
    val stopNotice: StateFlow<StopNotice?> get() = trip.notice

    init {
        trip.start()
        firebaseTrip?.shareMyPosition(route.positions)
    }
}
