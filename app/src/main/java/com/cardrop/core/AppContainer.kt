package com.cardrop.core

import android.content.Context
import com.cardrop.BuildConfig
import com.cardrop.ai.AiService
import com.cardrop.ai.ClaudeAiService
import com.cardrop.ai.FakeAiService
import com.cardrop.ai.FallbackAiService
import com.cardrop.ai.PhraseStore
import com.cardrop.ai.PrefsPhraseStore
import com.cardrop.ai.game.OmokCoordinator
import com.cardrop.ai.game.OmokResumeStore
import com.cardrop.ai.game.OmokRecoveringCrew
import com.cardrop.ai.game.FirebaseOmokTransport
import com.cardrop.ai.game.LocalOmokTransport
import com.cardrop.ai.game.PrefsOmokRoomStore
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.crew.FirebaseCrewRepository
import com.cardrop.core.demo.DemoCar
import com.cardrop.core.demo.DemoCrew
import com.cardrop.core.model.Place
import com.cardrop.core.navigation.AppNavigator
import com.cardrop.core.navigation.Screen
import com.cardrop.core.navigation.DropRoutingMemory
import com.cardrop.drive.BotFuel
import com.cardrop.drive.CrewFuel
import com.cardrop.drive.FirebaseTrip
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.PlaceTimes
import com.cardrop.drive.RouteSimulator
import com.cardrop.drive.StopNotice
import com.cardrop.drive.TripCoordinator
import com.cardrop.drive.GearFile
import com.cardrop.drive.TripCodeFile
import com.cardrop.drive.PrefsQuickDropStore
import com.cardrop.drive.QuickDrops
import com.cardrop.drop.SongPlayer
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
import com.cardrop.navi.PleosFusedLocationSource
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** Real crew or bot crew on this device: the DEMO panel's "크루" switch, else `cardrop.useRealCrew` ([DemoCrew]). */
    val useRealCrew: Boolean = DemoCrew.current(context)
    /**
     * [useRealCrew]: the real crew on Firebase, shared by several devices; this device is [myCar].
     * Otherwise the fake crew with bot cars (also the way back if the network fails).
     */
    private val omokResume = OmokResumeStore(context)
    private val baseCrew: CrewRepository =
        if (useRealCrew) FirebaseCrewRepository(appScope, FirebaseCrewRepository.carFor(myCar))
        else FakeCrewRepository(appScope)
    private val hadOmokResume = omokResume.read(baseCrew.myCarId, useRealCrew) != null
    private val recoveringCrew = OmokRecoveringCrew(baseCrew, appScope, omokResume, useRealCrew)
    val crew: CrewRepository = recoveringCrew
    val demoCrew: FakeCrewRepository? get() = baseCrew as? FakeCrewRepository

    // Lead
    /**
     * Real crew: every car's position and fuel, shared through Firebase (drive/FirebaseTrip.kt). When the trip
     * started is the crew's own (`Crew.startedAtMillis`), for both crews.
     */
    private val firebaseTrip: FirebaseTrip? = if (useRealCrew) FirebaseTrip(appScope, crew) else null
    /** Board 04's crew cards: the other cars' fuel, from their own devices (real crew) or the bots' ([BotFuel]). */
    val crewFuel: CrewFuel = firebaseTrip ?: BotFuel(appScope, crew.drops)

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
    private val crewLocation = PleosFusedLocationSource(context.applicationContext, appScope)
    val supportsFusedLocation: Boolean get() = crewLocation.supported

    /** A grant after the crew started connects the SDK without restarting the app or the trip. */
    fun onFusedLocationPermissionGranted() = crewLocation.onPermissionGranted()

    /** Crew diagram: fresh FusedLocation measurements, GPS fallback, and the other cars through Firebase. */
    val route: RouteSimulator = GpsCrewPositions(
        appScope, inAppNavigation, myCarId = { crew.myCarId }, shared = firebaseTrip?.positions,
        preferredLocation = crewLocation,
    )
    val navigation: NavigationHandoff = inAppNavigation

    /**
     * Board 01's "약 62km · 1시간 5분": road distance and drive time from this car to the crew destination, on
     * the navigator's live route. Null until that route is there.
     */
    val destinationTrip: StateFlow<DestinationTrip?> =
        combine(inAppNavigation.state, crew.crew) { state, current -> current?.destination?.let { tripToDestination(state, it) } }
            .stateIn(appScope, SharingStarted.Eagerly, null)

    /**
     * Boards 10 and 11: how far a place is from this car by road and how long it takes ("9km", "6분"), and the
     * crew's average ("12분 · 4대 평균"), on the navigator's live route. A new snapshot on every GPS fix.
     */
    val placeTimes: StateFlow<PlaceTimes> =
        combine(inAppNavigation.state, route.positions) { state, positions -> PlaceTimes(state, positions, crew.myCarId) }
            .stateIn(appScope, SharingStarted.Eagerly, PlaceTimes())

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
    /** What this car "plays" (boards 12, 13; no sound). Member A. */
    val songs = SongPlayer()
    val omok = OmokCoordinator(
        appScope, crew, vehicle,
        if (useRealCrew) FirebaseOmokTransport(context) else LocalOmokTransport(PrefsOmokRoomStore(context)),
        omokResume, useRealCrew,
    )

    /**
     * The open screen with its back stack, and which drops have already been opened. They live here, as long
     * as the crew and trip data, so a new activity (day/night theme, language, a second MainActivity on top)
     * keeps the driver on the same screen and doesn't open old drops again.
     */
    val navigator = AppNavigator(start = if (hadOmokResume) Screen.Games else DemoCar.startScreen(useRealCrew, myCar))
    val dropRouting = DropRoutingMemory({ crew.myCarId }, navigation.decisions::isSkipped)

    private val trip = TripCoordinator(appScope, crew, route, navigation, voice, autoStartsGuidance = true)

    /** This car got to the crew's stop, stopped there or drove past it: the card on the CarDrop side. */
    val stopNotice: StateFlow<StopNotice?> get() = trip.notice

    init {
        trip.start()
        appScope.launch {
            if (recoveringCrew.restored()) { omok.openGame(); navigator.switchTab(Screen.Games) }
            else if (hadOmokResume && navigator.current == Screen.Games) navigator.switchTab(DemoCar.startScreen(useRealCrew, myCar))
        }
        firebaseTrip?.shareMyFuel(vehicle.snapshot)
        firebaseTrip?.shareMyPosition(route.positions)
        // tools/drive_gps.py reads this car's crew code from the device (drive/TripCodeFile.kt). The bot crew
        // saves none, and an old code from an earlier start is removed.
        appScope.launch(Dispatchers.IO) {
            (firebaseTrip?.crewCode ?: flowOf(null)).collect { TripCodeFile.save(context, it) }
        }
        // tools/drive_gps.py also reads the gear (drive/GearFile.kt): the DEMO panel's P holds the demo car still.
        appScope.launch(Dispatchers.IO) {
            vehicle.snapshot.map { it.gear }.distinctUntilChanged().collect { GearFile.save(context, it) }
        }
    }
}
