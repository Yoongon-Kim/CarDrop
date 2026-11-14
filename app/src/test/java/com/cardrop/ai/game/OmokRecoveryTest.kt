package com.cardrop.ai.game

import com.cardrop.core.model.*
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class OmokRecoveryTest {
    private val destination = Place("p", "해변", 37.0, 128.0)
    private fun oldCrew() = Crew("123456", "여행", destination, "a", listOf(Car("a", "내 차", CarColor.BLUE)), startedAtMillis = 100)
    private class Store(var saved: OmokResume?) : OmokRecoveryStore {
        override fun read(car: String, real: Boolean, now: Long): OmokResume? = saved?.takeIf { it.car == car && it.real == real }
        override fun save(crew: Crew, car: String, real: Boolean) { saved = OmokResume(crew.code, crew.startedAtMillis!!, car, real, 0) }
        override fun clear() { saved = null }
    }
    private class Repo(var restoredCrew: Crew?) : CrewRepository {
        override val myCarId = "a"
        override val crew = MutableStateFlow<Crew?>(null)
        override val drops = MutableStateFlow<List<Drop>>(emptyList())
        var creates = 0
        var joins = 0
        var joinGate: CompletableDeferred<Unit>? = null
        var failJoin = false
        override suspend fun joinCrew(code: String): Crew? {
            joins++; joinGate?.await()
            if (failJoin) throw IllegalStateException("offline")
            return restoredCrew?.also { crew.value = it }
        }
        override suspend fun createCrew(name: String, destination: Place): Crew {
            creates++
            return Crew("654321", name, destination, myCarId, emptyList()).also { crew.value = it }
        }
        override fun leaveCrew() { crew.value = null }
        override suspend fun startTrip() = Unit
        override suspend fun setDestination(destination: Place) = Unit
        override suspend fun renameCrew(name: String) = Unit
        override suspend fun sendDrop(draft: DropDraft): Drop = error("unused")
        override suspend fun vote(dropId: String, candidateId: String) = Unit
    }
    @Test fun hostEnsureCrewWaitsForRecoveryInsteadOfMakingANewCode() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repo(oldCrew()).apply { joinGate = CompletableDeferred() }
            val store = Store(OmokResume("123456", 100, "a", true, 0))
            val recovering = OmokRecoveringCrew(repo, scope, store, true)
            val create = async { recovering.createCrew("new", destination) }
            yield()
            assertEquals(0, repo.creates)
            repo.joinGate!!.complete(Unit)
            assertEquals("123456", create.await().code)
            assertTrue(recovering.restored())
            assertEquals(0, repo.creates)
        } finally { scope.cancel() }
    }
    @Test fun reusedCrewCodeWithAnotherTripDoesNotRestoreTheWrongBoard() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repo(oldCrew().copy(startedAtMillis = 200))
            val store = Store(OmokResume("123456", 100, "a", true, 0))
            val recovering = OmokRecoveringCrew(repo, scope, store, true)
            assertFalse(recovering.restored())
            assertNull(repo.crew.value); assertNull(store.saved)
            recovering.createCrew("new", destination)
            assertEquals(1, repo.creates)
        } finally { scope.cancel() }
    }
    @Test fun transientNetworkFailurePreservesRecordAndDoesNotCreateANewCrew() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repo(oldCrew()).apply { failJoin = true }
            val store = Store(OmokResume("123456", 100, "a", true, 0))
            val recovering = OmokRecoveringCrew(repo, scope, store, true)
            assertNotNull(recovering.recoveryError.value)
            assertNotNull(store.saved)
            val create = async { recovering.createCrew("new", destination) }
            yield()
            assertFalse(create.isCompleted); assertEquals(0, repo.creates)
            repo.failJoin = false
            recovering.retry()
            assertEquals("123456", withTimeout(1_000) { create.await() }.code)
            assertNull(recovering.recoveryError.value)
        } finally { scope.cancel() }
    }
    @Test fun withoutAnOmokSessionExistingCrewCreationIsUnchanged() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repo(oldCrew())
            val recovering = OmokRecoveringCrew(repo, scope, Store(null), true)
            assertFalse(recovering.restored())
            assertEquals("654321", recovering.createCrew("new", destination).code)
            assertEquals(0, repo.joins); assertEquals(1, repo.creates)
        } finally { scope.cancel() }
    }
}
