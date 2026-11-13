package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Car
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.drop.VoteRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Owner: Member A.
 *
 * In-memory crew with 3 bot cars, so the whole flow works on one emulator with no network.
 * Bots join a few seconds after the crew is created and vote a few seconds after a drop.
 * The trip starts like the real crew's: with this car's 출발 (it is the host; a bot that hasn't joined by then stays
 * behind), or by itself once all 3 bots have joined (about 7 s after the crew is made). A crew joined with its code
 * (board 03) is on the road already.
 */
class FakeCrewRepository(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : CrewRepository {

    override val myCarId: String = DemoData.me.id

    private val _crew = MutableStateFlow<Crew?>(null)
    override val crew: StateFlow<Crew?> = _crew

    private val _drops = MutableStateFlow<List<Drop>>(emptyList())
    override val drops: StateFlow<List<Drop>> = _drops

    private val bots = listOf(DemoData.uncle, DemoData.minji, DemoData.aunt)

    /** The bots accepting the invite one by one; stopped when the trip starts. */
    private var botJoins: Job? = null

    init {
        // Closes votes whose deadline has passed.
        scope.launch {
            while (isActive) {
                delay(1_000)
                closeFinishedVotes()
            }
        }
    }

    override suspend fun createCrew(name: String, destination: Place): Crew {
        val crew = Crew(
            code = DemoData.DEMO_CREW_CODE,
            name = name,
            destination = destination,
            hostCarId = myCarId,
            cars = listOf(DemoData.me) + bots.map { it.copy(connected = false) },
            joinDeadlineMillis = now() + JoinRules.JOIN_WINDOW_MILLIS,
        )
        _crew.value = crew
        // Bots accept the invite one by one; 이모 차 is slow, like in board 01.
        botJoins?.cancel()
        botJoins = scope.launch {
            listOf(DemoData.uncle to 1_500L, DemoData.minji to 1_500L, DemoData.aunt to 4_000L)
                .forEach { (bot, wait) ->
                    delay(wait)
                    setConnected(bot)
                }
        }
        return crew
    }

    override suspend fun joinCrew(code: String): Crew? {
        delay(400)
        if (code.filter { it.isDigit() } != DemoData.DEMO_CREW_CODE) return null
        val crew = Crew(
            code = DemoData.DEMO_CREW_CODE,
            name = DemoData.DEMO_CREW_NAME,
            destination = DemoData.gyeongpo,
            hostCarId = DemoData.uncle.id,
            cars = listOf(DemoData.me) + bots,
            startedAtMillis = now(), // the bots are on the road already
        )
        _crew.value = crew
        return crew
    }

    override suspend fun startTrip() {
        botJoins?.cancel() // a bot that hasn't joined by now stays behind
        _crew.update { crew ->
            if (crew == null || crew.isStarted) return@update crew
            val missing = JoinRules.missingCars(crew)
            crew.copy(startedAtMillis = now(), cars = crew.cars.filterNot { it.id in missing })
        }
    }

    override suspend fun setDestination(destination: Place) {
        _crew.update { it?.copy(destination = destination) }
    }

    override suspend fun renameCrew(name: String) {
        _crew.update { it?.copy(name = name) }
    }

    override suspend fun sendDrop(draft: DropDraft): Drop {
        val current = _crew.value ?: error("크루에 먼저 참여해 주세요")
        if (!current.isStarted) throw TripNotStartedException() // no drops before 출발
        val drop = newDrop(fromCarId = myCarId, draft = draft)
        _drops.update { it + drop }
        if (drop.kind == DropKind.DECISION) botsVote(drop.id)
        return drop
    }

    override suspend fun vote(dropId: String, candidateId: String) {
        updateDrop(dropId) { it.copy(votes = it.votes + (myCarId to candidateId)) }
        closeFinishedVotes()
    }

    override fun leaveCrew() {
        botJoins?.cancel()
        _crew.value = null
        _drops.value = emptyList()
    }

    // ---- Demo controls (used by DemoPanel, not part of the interface) ----

    /**
     * Pretends another car sent a drop, e.g. 민지 차's low-fuel warning (board 06). Like [sendDrop], nothing before the
     * trip starts (the DEMO panel hides these buttons until then); a tap then does nothing instead of closing the app.
     */
    fun injectDrop(from: Car, draft: DropDraft) {
        if (_crew.value?.isStarted != true) return
        val drop = newDrop(fromCarId = from.id, draft = draft)
        _drops.update { it + drop }
        if (drop.kind == DropKind.DECISION) botsVote(drop.id, except = from.id)
    }

    /** Ends every open vote now, as if the timer ran out (silent cars count as candidate 1). */
    fun closeOpenVotesNow() {
        val time = now()
        _drops.update { list -> list.map { if (it.isDecided) it else it.copy(deadlineMillis = time) } }
        closeFinishedVotes()
    }

    // ---- internals ----

    private fun newDrop(fromCarId: String, draft: DropDraft): Drop {
        val created = now()
        return Drop(
            id = UUID.randomUUID().toString(),
            fromCarId = fromCarId,
            kind = draft.kind,
            topic = draft.topic,
            title = draft.title,
            message = draft.message,
            candidates = draft.candidates,
            createdAtMillis = created,
            deadlineMillis = created + draft.voteSeconds * 1_000L,
            toCarIds = draft.toCarIds,
        )
    }

    /** 삼촌 votes for #1 after 3 s, 민지 for #2 after 5 s, 이모 never answers (default #1). */
    private fun botsVote(dropId: String, except: String? = null) {
        scope.launch {
            delay(3_000)
            botVote(dropId, DemoData.uncle, rank = 0, except)
            delay(2_000)
            botVote(dropId, DemoData.minji, rank = 1, except)
        }
    }

    private fun botVote(dropId: String, bot: Car, rank: Int, except: String?) {
        // A bot left out of the trip (it hadn't joined by 출발) doesn't vote.
        val inTrip = _crew.value?.cars?.any { it.id == bot.id } == true
        if (bot.id == except || !inTrip) return
        updateDrop(dropId) { drop ->
            // A bot only answers drops that were sent to it.
            if (drop.isDecided || !drop.isFor(bot.id)) return@updateDrop drop
            val candidate: Candidate = drop.candidates.getOrNull(rank) ?: drop.candidates.firstOrNull()
                ?: return@updateDrop drop
            drop.copy(votes = drop.votes + (bot.id to candidate.id))
        }
        closeFinishedVotes()
    }

    private fun closeFinishedVotes() {
        val carIds = _crew.value?.cars?.map { it.id } ?: return
        val time = now()
        _drops.update { list ->
            list.map { drop ->
                // Only the sender and the cars it was sent to count in the vote.
                val voters = drop.voterIds(carIds)
                if (drop.kind == DropKind.DECISION && !drop.isDecided && drop.candidates.isNotEmpty() &&
                    VoteRules.isOver(drop, voters, time)
                ) {
                    drop.copy(decidedCandidateId = VoteRules.winner(drop, voters))
                } else {
                    drop
                }
            }
        }
    }

    private fun setConnected(bot: Car) {
        _crew.update { crew ->
            crew?.copy(cars = crew.cars.map { if (it.id == bot.id) it.copy(connected = true) else it })
        }
    }

    private fun updateDrop(dropId: String, change: (Drop) -> Drop) {
        _drops.update { list -> list.map { if (it.id == dropId) change(it) else it } }
    }

    companion object {
        /** Ready-made drafts for the demo panel. */
        fun demoFuelDraft(stations: List<Candidate>) = DropDraft(
            kind = DropKind.DECISION,
            topic = DropTopic.FUEL,
            title = "민지 차 연료가 부족해요 · 같이 갈래요?",
            message = "민지 차 · 주행 가능 85km · 덕평 주유소 9km",
            candidates = stations,
        )
    }
}
