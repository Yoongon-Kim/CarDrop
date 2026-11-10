package com.cardrop.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.ai.AiService
import com.cardrop.ai.TripContext
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.NavRoute
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.navi.NavigationHandoff
import com.cardrop.vehicle.VehicleRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owner: Member B. Screen state for boards 04, 05, 06 and 14, and the nav preview panel. */
class DriveViewModel(
    private val vehicle: VehicleRepository,
    route: RouteSimulator,
    private val navigation: NavigationHandoff,
    private val crewRepo: CrewRepository,
    private val ai: AiService,
) : ViewModel() {

    val snapshot: StateFlow<VehicleSnapshot> = vehicle.snapshot
    val positions: StateFlow<Map<String, CarPosition>> = route.positions
    val navRoute: StateFlow<NavRoute> = navigation.route
    val crew: StateFlow<Crew?> = crewRepo.crew
    val drops: StateFlow<List<Drop>> = crewRepo.drops
    val myCarId: String = crewRepo.myCarId

    init {
        vehicle.start()
    }

    override fun onCleared() {
        vehicle.stop()
    }

    /** Board 05: "응, 알려줘" -> the AI picks stations and the crew gets a fuel drop. */
    fun alertCrewAboutFuel(onSent: (Drop) -> Unit) {
        val crew = crew.value ?: return
        viewModelScope.launch {
            val stations = ai.recommend(DropTopic.FUEL, TripContext(crew, positions.value, snapshot.value))
            val drop = crewRepo.sendDrop(
                DropDraft(
                    kind = DropKind.DECISION,
                    topic = DropTopic.FUEL,
                    title = "연료가 부족해요 · 같이 주유할까요?",
                    message = "연료 ${snapshot.value.fuelPercent ?: "-"}% · 주행 가능 ${snapshot.value.rangeKm?.toInt() ?: "-"}km",
                    candidates = stations,
                ),
            )
            onSent(drop)
        }
    }

    /** Board 05: "아니, 혼자 갈게" -> route this car through the best station only. */
    fun goToStationAlone() {
        val crew = crew.value ?: return
        viewModelScope.launch {
            val best = ai.recommend(DropTopic.FUEL, TripContext(crew, positions.value, snapshot.value)).firstOrNull()
                ?: return@launch
            navigation.setRoute(crew.destination, best.place, "주유소를 경유지로 넣었어요")
            navigation.launch()
        }
    }

    /** Board 06: "같이 갈래요" -> vote for the first station and reroute now. */
    fun joinFuelStop(drop: Drop) {
        val crew = crew.value ?: return
        val station = drop.candidates.firstOrNull() ?: return
        viewModelScope.launch { crewRepo.vote(drop.id, station.id) }
        navigation.setRoute(crew.destination, station.place, "경유지를 ${station.place.name}로 바꿨어요")
        navigation.launch()
    }

    /** Board 14: "우리 차는 안 들르기". */
    fun skipStop() {
        val crew = crew.value ?: return
        navigation.setRoute(crew.destination, waypoint = null, reason = "우리 차는 바로 목적지로 가요")
        navigation.launch()
    }

    /** Boards 01, 03, 14: "출발하기" / "지금 안내 시작" -> open the map app on the crew route. */
    fun startGuidance() {
        navigation.launch()
    }
}
