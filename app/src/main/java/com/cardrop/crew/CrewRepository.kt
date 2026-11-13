package com.cardrop.crew

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner: Member A.
 *
 * Everything that travels between cars: the crew, drops and votes.
 * FakeCrewRepository keeps it in memory with bot cars. The real version syncs through a
 * realtime database (Firebase Realtime Database, or Supabase as the fallback) so two emulators
 * see the same crew.
 */
interface CrewRepository {
    /** The car this app runs in. */
    val myCarId: String

    /** Null until we create or join a crew. */
    val crew: StateFlow<Crew?>

    /** All drops in the crew, oldest first. Votes and decisions update in place. */
    val drops: StateFlow<List<Drop>>

    /** Board 01: a new crew with this car as its host. The other cars have 3 minutes to join (`Crew.joinDeadlineMillis`). */
    suspend fun createCrew(name: String, destination: Place): Crew

    /**
     * Returns null when no crew uses this code. Throws [CrewClosedException] when the crew's trip started without
     * this car (it didn't join in time). A car that was in the trip can join again, e.g. after its app restarted.
     */
    suspend fun joinCrew(code: String): Crew?

    /**
     * The trip starts for the whole crew: the host's 출발 (board 01), or by itself (TripCoordinator) once every car
     * has joined or the join deadline has passed. The cars that haven't joined yet are left out
     * (`JoinRules.missingCars`): they drop out of [crew] on every device.
     * Does nothing when the trip has started already.
     */
    suspend fun startTrip()

    suspend fun setDestination(destination: Place)

    /** Board 01's 바꾸기: the crew's new name (e.g. "강릉 여행" -> "가족 여행"), for every car in it. */
    suspend fun renameCrew(name: String)

    suspend fun sendDrop(draft: DropDraft): Drop

    suspend fun vote(dropId: String, candidateId: String)

    fun leaveCrew()
}

/** [CrewRepository.joinCrew]: the crew set off without this car, which didn't join within 3 minutes. */
class CrewClosedException : Exception("이미 출발한 크루예요")

/** What a car fills in before sending a drop. The repository adds ids and timestamps. */
data class DropDraft(
    val kind: DropKind,
    val topic: DropTopic,
    val title: String,
    val message: String = "",
    val candidates: List<Candidate> = emptyList(),
    /** Design board 11 shows a 2-minute vote. */
    val voteSeconds: Int = 120,
    /** The cars to send it to besides this one (board 08 "받는 차"). Empty = the whole crew. */
    val toCarIds: List<String> = emptyList(),
)
