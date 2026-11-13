package com.cardrop.drive

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.navi.NavigationHandoff
import com.cardrop.navi.WaypointEvent
import com.cardrop.navi.WaypointResult
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owner: Member B.
 *
 * Runs for the whole app, whatever screen is open:
 * - the crew destination changes -> record it as the route (the driver opens the map app)
 * - the crew forms -> start moving the cars on the route
 * - a vote ends -> record the winner as the waypoint and say it aloud
 * - this car gets to the waypoint -> show it ([notice]); it stops there or drives past it -> say it aloud,
 *   tell the crew, and show that too
 */
class TripCoordinator(
    private val scope: CoroutineScope,
    private val crew: CrewRepository,
    private val route: RouteSimulator,
    private val navigation: NavigationHandoff,
    private val voice: VoiceService,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var job: Job? = null

    private val _notice = MutableStateFlow<StopNotice?>(null)

    /** What the CarDrop side shows about the crew's stop ([StopNoticeCard]); null = nothing. */
    val notice: StateFlow<StopNotice?> = _notice

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            launch { navigation.waypointEvents.collect(::onWaypoint) }
            launch {
                navigation.reachedWaypoint.collect { _notice.value = StopNotice(it, StopNotice.Kind.AT_STOP, atMillis = now()) }
            }
            var previousKey: String? = null
            var previousCars = emptyList<String>()
            var previousDestination: Place? = null
            combine(crew.crew, crew.drops) { current, drops -> current to drops }.collect { (current, drops) ->
                val key = current?.let { "${it.code}:${it.hostCarId}" }
                if (key != previousKey) {
                    _notice.value = null
                    navigation.clear()
                    route.stop()
                    navigation.decisions.forCrew(key)
                    previousCars = emptyList()
                    previousDestination = null
                    previousKey = key
                }
                if (current == null) return@collect
                if (current.destination != previousDestination) {
                    navigation.setRoute(current.destination, navigation.route.value.waypoint, "크루 목적지를 내비에 넣었어요")
                }
                val cars = current.cars.map { it.id }
                if (cars != previousCars || current.destination != previousDestination) route.start(cars, current.destination)
                previousCars = cars
                previousDestination = current.destination

                val pending = drops.filter { it.isFor(crew.myCarId) && it.kind == DropKind.DECISION && it.isDecided &&
                    it.decidedCandidate != null && !navigation.decisions.wasHandled(it.id) }
                // Reconnect can deliver the entire history together. Apply only the newest
                // relevant decision, rather than replaying every old waypoint and announcement.
                val latest = pending.filter { navigation.decisions.canApply(it) && involved(it) }
                    .maxWithOrNull(compareBy<Drop> { it.createdAtMillis }.thenBy { it.id })
                pending.filter { it != latest }.forEach { navigation.decisions.markHandled(it) }
                if (latest != null) {
                    onDecided(latest)
                    navigation.decisions.markHandled(latest, applied = true)
                }
            }
        }
    }

    /**
     * The driver hears it; the other cars get a quiet on-screen message (a SOCIAL drop, never spoken),
     * shown as e.g. "아빠 차 · 여주휴게소에 도착했어요".
     */
    private suspend fun onWaypoint(event: WaypointEvent) {
        // The crew text comes from StopNews, which board 14 also uses to read it back.
        val (spoken, forCrew) = when (event.result) {
            WaypointResult.ARRIVED -> StopNews.arrived(event.place).let { it to it }
            WaypointResult.PASSED -> "${StopNews.passed(event.place)}. 목적지로 안내할게요" to StopNews.passed(event.place)
        }
        runCatching { voice.speak(spoken) }
        val told = runCatching { crew.sendDrop(DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = forCrew)) }.isSuccess
        val kind = if (event.result == WaypointResult.ARRIVED) StopNotice.Kind.ARRIVED else StopNotice.Kind.PASSED
        _notice.value = StopNotice(event.place, kind, crewTold = told, atMillis = now())
    }

    private fun involved(drop: Drop): Boolean = drop.topic != DropTopic.FUEL ||
        crew.myCarId in drop.votes || drop.fromCarId == crew.myCarId

    private fun onDecided(drop: Drop) {
        val place = drop.decidedCandidate?.place ?: return
        val currentCrew = crew.crew.value ?: return

        // A fuel drop only reroutes the cars that chose "같이 갈래요" (board 06).
        if (drop.topic == DropTopic.FUEL && crew.myCarId !in drop.votes && drop.fromCarId != crew.myCarId) return

        if (drop.topic == DropTopic.DESTINATION) {
            if (currentCrew.cars.all { drop.isFor(it.id) }) {
                scope.launch { crew.setDestination(place) }
            } else {
                // A destination chosen by only some cars must not change the entire crew.
                navigation.setRoute(place, waypoint = null, reason = "우리 차 목적지를 ${place.name}로 바꿨어요")
            }
        } else {
            val destination = navigation.route.value.destination ?: currentCrew.destination
            navigation.setRoute(destination, waypoint = place, reason = "경유지를 ${place.name}로 바꿨어요")
        }
        runCatching { voice.speak("${place.name}로 결정되었어요") }
    }
}
