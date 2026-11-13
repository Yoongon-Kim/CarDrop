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

    suspend fun createCrew(name: String, destination: Place): Crew

    /** Returns null when no crew uses this code. */
    suspend fun joinCrew(code: String): Crew?

    suspend fun setDestination(destination: Place)

    /** Board 01's 바꾸기: the crew's new name (e.g. "강릉 여행" -> "가족 여행"), for every car in it. */
    suspend fun renameCrew(name: String)

    suspend fun sendDrop(draft: DropDraft): Drop

    suspend fun vote(dropId: String, candidateId: String)

    fun leaveCrew()
}

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
