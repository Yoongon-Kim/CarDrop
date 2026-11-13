package com.cardrop.drive

import com.cardrop.ai.AiService
import com.cardrop.ai.TripContext
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.*
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.navi.NavigationHandoff
import com.cardrop.navi.WaypointEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestCrew : CrewRepository {
    override val myCarId = DemoData.me.id
    override val crew = MutableStateFlow<Crew?>(Crew("123456", "test", DemoData.gyeongpo, myCarId, listOf(DemoData.me, DemoData.uncle)))
    override val drops = MutableStateFlow<List<Drop>>(emptyList())
    var sends = 0
    var votes = 0
    override suspend fun createCrew(name: String, destination: Place) = crew.value!!
    override suspend fun joinCrew(code: String) = crew.value
    override suspend fun setDestination(destination: Place) { crew.value = crew.value?.copy(destination = destination) }
    override suspend fun sendDrop(draft: DropDraft): Drop {
        sends++
        val drop = Drop("sent-$sends", myCarId, draft.kind, draft.topic, draft.title, draft.message, draft.candidates,
            createdAtMillis = sends.toLong(), deadlineMillis = Long.MAX_VALUE, toCarIds = draft.toCarIds)
        drops.value += drop
        return drop
    }
    override suspend fun vote(dropId: String, candidateId: String) {
        votes++
        drops.value = drops.value.map { if (it.id == dropId) it.copy(votes = it.votes + (myCarId to candidateId)) else it }
    }
    override fun leaveCrew() { crew.value = null; drops.value = emptyList() }
}

internal class TestNavigation : NavigationHandoff {
    override val route = MutableStateFlow(NavRoute())
    override val decisions = TripDecisions()
    override val isAvailable = true
    var launches = 0
    var available = true
    val changes = mutableListOf<NavRoute>()
    override fun clear() { route.value = NavRoute() }
    override fun setRoute(destination: Place, waypoint: Place?, reason: String?) {
        route.value = NavRoute(destination, waypoint, reason)
        changes += route.value
    }
    override fun launch(): Boolean { launches++; return available && route.value.destination != null }
    override val waypointEvents = MutableSharedFlow<WaypointEvent>(extraBufferCapacity = 8)
}

internal class TestRoute : RouteSimulator {
    private val here = DemoRoute.pointAt(40.0)
    override val positions = MutableStateFlow(mapOf(DemoData.me.id to CarPosition(DemoData.me.id, here.lat, here.lng, (DemoRoute.totalKm - 40).toFloat(), 120)))
    var starts = 0
    var stops = 0
    override fun start(carIds: List<String>, destination: Place) { starts++ }
    override fun stop() { stops++ }
}

internal class TestVoice : VoiceService {
    override val lastSpoken = MutableStateFlow<String?>(null)
    override val listening = MutableStateFlow(false)
    val spoken = mutableListOf<String>()
    override fun speak(text: String) { spoken += text; lastSpoken.value = text }
    override suspend fun listen(placeName: Boolean): String? = null
}

internal class TestAi : AiService {
    var requests = 0
    var gate: CompletableDeferred<Unit>? = null
    var fail = false
    var empty = false
    override suspend fun recommend(topic: DropTopic, context: TripContext): List<Candidate> {
        requests++
        gate?.await()
        check(!fail) { "추천 서비스 연결 실패" }
        if (empty) return emptyList()
        return context.pool.take(3).map { Candidate(it.id, it, "test", 2) }
    }
    override suspend fun readIntent(utterance: String) = DriverIntent(DropTopic.FUEL, 1f)
    override suspend fun polishMessage(raw: String) = raw
    override suspend fun localQuiz(regionName: String): QuizQuestion = error("unused")
}

internal fun decision(id: String, topic: DropTopic = DropTopic.REST, from: String = DemoData.uncle.id,
    at: Long = 1, voted: Boolean = false, decided: Boolean = true): Drop {
    val place = DemoData.restStops[if (id == "new") 1 else 0]
    val candidate = Candidate(place.id, place, "test", 2)
    return Drop(id, from, DropKind.DECISION, topic, "test", candidates = listOf(candidate),
        votes = if (voted) mapOf(DemoData.me.id to candidate.id) else emptyMap(),
        createdAtMillis = at, deadlineMillis = Long.MAX_VALUE, decidedCandidateId = if (decided) candidate.id else null)
}
