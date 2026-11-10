package com.cardrop.drive

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.crew.CrewRepository
import com.cardrop.navi.NavigationHandoff
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owner: Member B.
 *
 * Runs for the whole app, whatever screen is open:
 * - the crew destination changes -> record it as the route (the driver opens the map app)
 * - the crew forms -> start moving the cars on the route
 * - a vote ends -> record the winner as the waypoint and say it aloud
 */
class TripCoordinator(
    private val scope: CoroutineScope,
    private val crew: CrewRepository,
    private val route: RouteSimulator,
    private val navigation: NavigationHandoff,
    private val voice: VoiceService,
) {
    private val handledDecisions = mutableSetOf<String>()

    fun start() {
        scope.launch {
            crew.crew.map { it?.destination }.distinctUntilChanged().collect { destination ->
                if (destination != null) {
                    navigation.setRoute(destination, navigation.route.value.waypoint, "크루 목적지를 내비에 넣었어요")
                }
            }
        }
        scope.launch {
            crew.crew.map { c -> c?.let { it.cars.map { car -> car.id } to it.destination } }
                .distinctUntilChanged()
                .collect { value ->
                    if (value == null) route.stop() else route.start(value.first, value.second)
                }
        }
        scope.launch {
            crew.drops.collect { drops ->
                drops.filter { it.kind == DropKind.DECISION && it.isDecided && it.id !in handledDecisions }
                    .forEach { onDecided(it) }
            }
        }
    }

    private fun onDecided(drop: Drop) {
        handledDecisions += drop.id
        val place = drop.decidedCandidate?.place ?: return
        val destination = crew.crew.value?.destination ?: return

        // A fuel drop only reroutes the cars that chose "같이 갈래요" (board 06).
        if (drop.topic == DropTopic.FUEL && crew.myCarId !in drop.votes && drop.fromCarId != crew.myCarId) return

        if (drop.topic == DropTopic.DESTINATION) {
            scope.launch { crew.setDestination(place) }
        } else {
            navigation.setRoute(destination, waypoint = place, reason = "경유지를 ${place.name}로 바꿨어요")
        }
        voice.speak("${place.name}로 결정되었어요")
    }
}
