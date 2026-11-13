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
    val myCarId = crewRepo.myCarId
    val drops: StateFlow<List<Drop>> = crewRepo.drops.map { list -> list.filter { it.isFor(myCarId) } }
        .stateIn(scope, SharingStarted.Eagerly, crewRepo.drops.value.filter { it.isFor(myCarId) })
    private val liveNavigation = navigation as? InAppNavigation
    private val warnings = FuelWarningTracker()
    private val _fuelWarningPending = MutableStateFlow(false)
    val fuelWarningPending: StateFlow<Boolean> = _fuelWarningPending
    private val _action = MutableStateFlow(DriveActionState())
    val action: StateFlow<DriveActionState> = _action

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

    private suspend fun recommendStations(shared: Boolean): List<Candidate> {
        val currentCrew = requireCrew()
        val pool = eligibleStations(shared)
        check(pool.isNotEmpty()) { "남은 주행 거리 안에 있는 전방 주유소를 찾지 못했어요" }
        val picks = ai.recommend(DropTopic.FUEL, TripContext(currentCrew, positions.value, snapshot.value, pool))
        val known = eligibleStations(shared).associateBy { it.id }
        val result = mutableListOf<Pair<Candidate, Double?>>()
        for (pick in picks.distinctBy { it.place.id }) {
            val place = known[pick.place.id] ?: continue
            // Confirm the station's actual driving route, including its access ramp.
            val drivingKm = liveNavigation?.fuelStopDistanceKm(place)
            result += pick.copy(id = place.id, place = place) to drivingKm
        }
        // Fuel and position can change during AI/routing requests. Validate again before sending.
        val stillAhead = eligibleStations(shared).map { it.id }.toSet()
        val remaining = snapshot.value.rangeKm ?: 0f
        val safe = result.filter { (candidate, km) -> candidate.place.id in stillAhead && (km == null || km + 5 <= remaining) }
        check(safe.isNotEmpty()) { "도달 가능한 주유소 후보가 없어요. 위치와 연료를 확인하고 다시 시도해 주세요" }
        return safe.take(3).map { it.first }
    }

    fun alertCrewAboutFuel(onSent: (Drop) -> Unit) = perform("갈 수 있는 주유소를 찾고 있어요…") {
        val key = crewKey()
        val stations = recommendStations(shared = true)
        ensureCrew(key)
        val car = snapshot.value
        val drop = crewRepo.sendDrop(DropDraft(
            kind = DropKind.DECISION, topic = DropTopic.FUEL,
            title = "연료가 부족해요 · 같이 주유할까요?",
            message = "연료 ${car.fuelPercent ?: "—"}% · 주행 가능 ${car.rangeKm?.toInt() ?: "—"}km",
            candidates = stations,
        ))
        ensureCrew(key)
        acknowledgeFuelWarning()
        onSent(drop)
    }

    fun goToStationAlone(onDone: () -> Unit = {}) = perform("우리 차가 갈 주유소를 찾고 있어요…") {
        val key = crewKey()
        val best = recommendStations(shared = false).first()
        ensureCrew(key)
        val destination = navigation.route.value.destination ?: requireCrew().destination
        navigation.setRoute(destination, best.place, "우리 차만 ${best.place.name}에 들러요")
        check(navigation.launch()) { navigation.route.value.lastAction ?: "길안내를 시작할 수 없어요" }
        acknowledgeFuelWarning()
        onDone()
    }

    /** Vote only. TripCoordinator applies the final winner after the vote closes. */
    fun joinFuelStop(drop: Drop, onDone: () -> Unit = {}) = perform("함께 가겠다고 알리고 있어요…") {
        val current = drops.value.firstOrNull { it.id == drop.id } ?: error("연료 알림을 찾을 수 없어요")
        check(!current.isDecided) { "이미 결정된 알림이에요. 결정 화면을 확인해 주세요" }
        check(!skipped(drop.id)) { "이미 가던 길로 가기로 했어요" }
        val station = current.candidates.firstOrNull() ?: error("주유소 후보가 없어요")
        val key = crewKey()
        crewRepo.vote(current.id, station.id)
        ensureCrew(key)
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

    private fun guidanceError(message: String): Boolean {
        _action.value = DriveActionState(error = message)
        return false
    }
}

data class DriveActionState(val busy: Boolean = false, val message: String? = null, val error: String? = null)
