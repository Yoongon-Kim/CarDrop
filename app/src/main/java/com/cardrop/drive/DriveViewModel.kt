package com.cardrop.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.ai.AiService
import com.cardrop.ai.TripContext
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.*
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.navi.InAppNavigation
import com.cardrop.navi.NavigationHandoff
import com.cardrop.vehicle.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

/** State shared by boards 04, 05, 06 and 14. Services are supplied by AppContainer. */
class DriveViewModel(
    private val vehicle: VehicleRepository,
    route: RouteSimulator,
    private val navigation: NavigationHandoff,
    private val crewRepo: CrewRepository,
    private val ai: AiService,
    workScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = workScope ?: viewModelScope
    val snapshot = vehicle.snapshot
    val positions = route.positions
    val navRoute = navigation.route
    val crew = crewRepo.crew
    /**
     * Read every time, never copied: with the real crew the car is picked when joining (board 03),
     * after this ViewModel is made at app start. Joining changes [crew], so [drops] are filtered
     * again for the new car then.
     */
    val myCarId: String get() = crewRepo.myCarId
    val drops: StateFlow<List<Drop>> = combine(crewRepo.drops, crewRepo.crew) { list, _ -> list.filter { it.isFor(myCarId) } }
        .stateIn(scope, SharingStarted.Eagerly, crewRepo.drops.value.filter { it.isFor(myCarId) })
    private val liveNavigation = navigation as? InAppNavigation
    private val warnings = FuelWarningTracker()
    private val _fuelWarningPending = MutableStateFlow(false)
    val fuelWarningPending: StateFlow<Boolean> = _fuelWarningPending
    private val _action = MutableStateFlow(DriveActionState())
    val action: StateFlow<DriveActionState> = _action
    private val _fuelPlan = MutableStateFlow<FuelPlan?>(null)
    /** Board 05 step 2: the stations the AI picked, waiting for the driver's choice. Null while on step 1. */
    val fuelPlan: StateFlow<FuelPlan?> = _fuelPlan

    val inputMode: StateFlow<DrivingInput> = combine(
        snapshot, liveNavigation?.state ?: MutableStateFlow(null),
    ) { car, nav -> drivingInput(car.gear, nav?.fix, System.currentTimeMillis()) }
        .stateIn(scope, SharingStarted.Eagerly, drivingInput(snapshot.value.gear, null, System.currentTimeMillis()))

    init {
        vehicle.start()
        scope.launch {
            var crewKey: String? = null
            combine(crew, snapshot) { current, car -> current to car }.collect { (current, car) ->
                val key = current?.let { "${it.code}:${it.hostCarId}" }
                if (key != crewKey) {
                    warnings.reset()
                    _fuelWarningPending.value = false
                    _fuelPlan.value = null
                    crewKey = key
                }
                if (current != null && warnings.update(car.fuelPercent)) _fuelWarningPending.value = true
                if (car.fuelPercent?.let { it >= 20 } == true) _fuelWarningPending.value = false
            }
        }
    }

    override fun onCleared() { vehicle.stop() }
    fun acknowledgeFuelWarning() { _fuelWarningPending.value = false }
    fun clearError() { if (!_action.value.busy) _action.value = DriveActionState() }

    /** Back to board 05 step 1, or a fresh start when the screen opens. */
    fun cancelFuelPlan() {
        if (_action.value.busy) return
        _fuelPlan.value = null
        _action.value = DriveActionState()
    }
    fun skipped(dropId: String) = navigation.decisions.isSkipped(dropId)

    private fun crewKey() = crew.value?.let { "${it.code}:${it.hostCarId}" }
    private fun requireCrew() = crew.value ?: error("크루를 먼저 만들어 주세요")

    private fun perform(message: String, block: suspend () -> Unit) {
        if (_action.value.busy) return
        val key = crewKey()
        _action.value = DriveActionState(busy = true, message = message)
        scope.launch {
            try {
                requireCrew()
                block()
                _action.value = DriveActionState()
            } catch (e: CancellationException) {
                _action.value = DriveActionState()
                throw e
            } catch (e: Exception) {
                val message = when {
                    key != crewKey() -> "크루가 변경됐어요. 다시 시도해 주세요"
                    e is java.io.IOException -> "인터넷 연결을 확인하고 다시 시도해 주세요"
                    e is IllegalStateException -> e.message ?: "처리하지 못했어요. 다시 시도해 주세요"
                    else -> "처리하지 못했어요. 잠시 후 다시 시도해 주세요"
                }
                _action.value = DriveActionState(error = message)
            }
        }
    }

    private fun ensureCrew(key: String?) { check(key != null && key == crewKey()) { "크루가 변경됐어요. 다시 시도해 주세요" } }

    private fun eligibleStations(shared: Boolean): List<Place> {
        val range = snapshot.value.rangeKm
        check(range != null && range.isFinite() && range > 5) { "주행 가능 거리를 확인할 수 없어요. 연료와 차량 연결을 확인해 주세요" }
        val geometry: RouteGeometry
        val progress: Double
        if (liveNavigation != null) {
            val state = liveNavigation.state.value
            val fix = state.fix ?: error("GPS 위치를 기다리고 있어요")
            check(System.currentTimeMillis() - fix.timeMillis in 0..10_000) { "GPS 위치를 다시 확인해 주세요" }
            geometry = state.route?.geometry ?: error("경로를 찾은 후 다시 시도해 주세요")
            val here = geometry.locate(fix.lat, fix.lng)
            check(here.offMeters <= 1_500) { "현재 경로를 다시 찾은 후 시도해 주세요" }
            progress = here.km
        } else {
            val here = positions.value[myCarId] ?: error("차량 위치를 기다리고 있어요")
            geometry = RouteGeometry(DemoRoute.points)
            progress = geometry.locate(here.lat, here.lng).km
        }
        // The shared vote lasts 2 minutes; the 6x demo can travel 20 km during it.
        return reachableFuelStops(DemoData.restStops, geometry, progress, range, if (shared) 25.0 else 0.5)
    }

    private suspend fun recommendStations(shared: Boolean): List<FuelStation> {
        val currentCrew = requireCrew()
        val pool = eligibleStations(shared)
        check(pool.isNotEmpty()) { "남은 주행 거리 안에 있는 전방 주유소를 찾지 못했어요" }
        val picks = ai.recommend(DropTopic.FUEL, TripContext(currentCrew, positions.value, snapshot.value, pool))
        val known = eligibleStations(shared).associateBy { it.id }
        val result = mutableListOf<FuelStation>()
        for (pick in picks.distinctBy { it.place.id }) {
            val place = known[pick.place.id] ?: continue
            // Confirm the station's actual driving route, including its access ramp.
            val trip = liveNavigation?.fuelStopTrip(place)
            result += FuelStation(pick.copy(id = place.id, place = place), trip?.km, trip?.minutes)
        }
        // Fuel and position can change during AI/routing requests. Validate again before showing them.
        val safe = stillReachable(result, shared)
        check(safe.isNotEmpty()) { "도달 가능한 주유소 후보가 없어요. 위치와 연료를 확인하고 다시 시도해 주세요" }
        return safe.take(3)
    }

    /** Stations still ahead of this car and reachable with a 5 km reserve. */
    private fun stillReachable(stations: List<FuelStation>, shared: Boolean): List<FuelStation> {
        val ahead = eligibleStations(shared).map { it.id }.toSet()
        val remaining = snapshot.value.rangeKm ?: 0f
        return stations.filter { it.candidate.place.id in ahead && (it.drivingKm == null || it.drivingKm + 5 <= remaining) }
    }

    /** Board 05 step 1: the AI picks up to 3 stations. Nothing is sent or routed until the driver chooses one. */
    fun planFuelStop(shared: Boolean) = perform("갈 수 있는 주유소를 찾고 있어요…") {
        val key = crewKey()
        val stations = recommendStations(shared)
        ensureCrew(key)
        _fuelPlan.value = FuelPlan(shared, stations)
    }

    /**
     * Board 05 step 2. Shared ("여기로 정하고 일행에게 알리기"): the crew gets a fuel drop for the chosen
     * station. Alone ("여기로 혼자 다녀오기"): only this car goes there.
     */
    fun confirmFuelStop(index: Int, onDone: () -> Unit = {}) {
        val plan = _fuelPlan.value ?: return
        perform(if (plan.shared) "일행에게 알리고 있어요…" else "주유소를 경유지로 넣고 있어요…") {
            val key = crewKey()
            val chosen = plan.stations.getOrNull(index) ?: error("주유소를 다시 골라 주세요")
            val place = chosen.candidate.place
            // The car kept driving while the driver chose: the station must still be ahead and reachable.
            if (stillReachable(listOf(chosen), plan.shared).isEmpty()) {
                _fuelPlan.value = plan.copy(stations = plan.stations - chosen).takeIf { it.stations.isNotEmpty() }
                error("${place.name}에는 이제 갈 수 없어요. 다른 곳을 골라 주세요")
            }
            if (plan.shared) {
                val car = snapshot.value
                val distance = chosen.drivingKm?.let { " ${it.roundToInt()}km" }.orEmpty()
                crewRepo.sendDrop(DropDraft(
                    kind = DropKind.DECISION, topic = DropTopic.FUEL,
                    title = "연료가 부족해요 · 같이 주유할까요?",
                    message = "연료 ${car.fuelPercent ?: "—"}% · 주행 가능 ${car.rangeKm?.toInt() ?: "—"}km · ${place.name}$distance",
                    candidates = listOf(chosen.candidate),
                ))
            } else {
                val destination = navigation.route.value.destination ?: requireCrew().destination
                navigation.setRoute(destination, place, "우리 차만 ${place.name}에 들러요")
                check(navigation.launch()) { navigation.route.value.lastAction ?: "길안내를 시작할 수 없어요" }
            }
            ensureCrew(key)
            _fuelPlan.value = null
            acknowledgeFuelWarning()
            onDone()
        }
    }

    /**
     * Board 06 "같이 갈래요": vote for the station and put it into this car's navigator right away, as in the design.
     * The vote still decides: if another station wins, TripCoordinator moves the waypoint there.
     */
    fun joinFuelStop(drop: Drop, onDone: () -> Unit = {}) = perform("함께 가겠다고 알리고 있어요…") {
        val current = drops.value.firstOrNull { it.id == drop.id } ?: error("연료 알림을 찾을 수 없어요")
        check(!current.isDecided) { "이미 결정된 알림이에요. 결정 화면을 확인해 주세요" }
        check(!skipped(drop.id)) { "이미 가던 길로 가기로 했어요" }
        val station = current.candidates.firstOrNull() ?: error("주유소 후보가 없어요")
        val key = crewKey()
        crewRepo.vote(current.id, station.id)
        ensureCrew(key)
        val destination = navigation.route.value.destination ?: requireCrew().destination
        navigation.setRoute(destination, station.place, "경유지를 ${station.place.name}로 바꿨어요")
        onDone()
    }

    fun declineFuelStop(dropId: String) { navigation.decisions.skip(dropId) }

    fun skipStop(dropId: String? = null): Boolean {
        if (dropId != null) navigation.decisions.skip(dropId)
        val current = crew.value ?: return guidanceError("크루를 먼저 만들어 주세요")
        val destination = navigation.route.value.destination ?: current.destination
        navigation.setRoute(destination, null, "우리 차는 바로 목적지로 가요")
        return startGuidance()
    }

    fun startGuidance(): Boolean {
        if (navigation.route.value.destination == null) return guidanceError("목적지가 아직 없어요")
        if (!navigation.launch()) return guidanceError(navigation.route.value.lastAction ?: "길안내를 시작할 수 없어요")
        _action.value = DriveActionState()
        return true
    }

    /**
     * True when guidance may start without a tap (board 14's 3-second countdown): the in-app navigator.
     * Handing the route to another map app switches apps, which needs the driver's own tap (see CLAUDE.md).
     */
    val autoStartsGuidance: Boolean get() = liveNavigation != null

    /** Board 14 "도착 예정": when each car reaches [drop]'s decided place. */
    fun arrivals(drop: Drop): List<Arrival> {
        val place = drop.decidedCandidate?.place ?: return emptyList()
        val current = crew.value ?: return emptyList()
        val way = myWayTo(place)
        return crewArrivals(
            drop, current.cars, positions.value, myCarId, skipped(drop.id), way?.first, way?.second,
            navigation.route.value.destination?.name ?: current.destination.name,
            System.currentTimeMillis(), ZoneId.systemDefault(),
            said = StopNews.results(drops.value, place, sinceMillis = drop.createdAtMillis),
        )
    }

    /** This car's distance (km) and time (minutes) to [place] along the trip; null when not known yet. */
    private fun myWayTo(place: Place): Pair<Double, Double>? {
        if (liveNavigation != null) {
            val state = liveNavigation.state.value
            val live = state.route ?: return null
            val placeKm = live.waypointKm?.takeIf { navigation.route.value.waypoint?.id == place.id }
                ?: live.geometry.locate(place.lat, place.lng).takeIf { it.offMeters < 1_500 }?.km
                ?: return null
            return (placeKm - state.progressKm) to live.secondsBetween(state.progressKm, placeKm) / 60
        }
        val me = positions.value[myCarId] ?: return null
        val placeLeftKm = DemoRoute.totalKm - RouteGeometry(DemoRoute.points).locate(place.lat, place.lng).km
        val km = me.kmToDestination - placeLeftKm
        return km to if (me.kmToDestination > 0) me.etaMinutes * km / me.kmToDestination else 0.0
    }

    private fun guidanceError(message: String): Boolean {
        _action.value = DriveActionState(error = message)
        return false
    }
}

data class DriveActionState(val busy: Boolean = false, val message: String? = null, val error: String? = null)

/** A station the AI picked for board 05, with its real driving distance and time when the navigator knows them. */
data class FuelStation(val candidate: Candidate, val drivingKm: Double? = null, val minutes: Int? = null)

/** Board 05 step 2. [shared] = "응, 알려줘" (tell the crew); false = "아니, 혼자 갈게". */
data class FuelPlan(val shared: Boolean, val stations: List<FuelStation>)
