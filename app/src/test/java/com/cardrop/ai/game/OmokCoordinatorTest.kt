package com.cardrop.ai.game

import com.cardrop.core.model.*
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.FakeCrewRepository
import com.cardrop.vehicle.FakeVehicleRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class OmokCoordinatorTest {
    private class Store : OmokRoomStore {
        private val data = mutableMapOf<String, String>()
        override fun read(key: String) = data[key]
        override fun write(key: String, value: String) { data[key] = value }
    }
    private fun fixture(scope: CoroutineScope): Pair<OmokCoordinator, LocalOmokTransport> {
        val crew = object : CrewRepository by FakeCrewRepository(scope) {
            override val myCarId = "a"
            override val crew = MutableStateFlow<Crew?>(Crew("123456", "여행", Place("p", "해변", 37.0, 128.0), "a",
                listOf(Car("a", "내 차", CarColor.BLUE), Car("b", "상대", CarColor.YELLOW)), startedAtMillis = 100))
        }
        val vehicle = FakeVehicleRepository().apply { setGear(Gear.PARK) }
        val transport = LocalOmokTransport(Store()) { 10_000 }
        return OmokCoordinator(scope, crew, vehicle, transport, false) { 1_000 } to transport
    }
    @Test fun disposingOldActivityAfterNewActivityStartsDoesNotPauseItsGame() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val (omok, _) = fixture(scope)
            omok.onVisible("old-activity", true); omok.onGameVisible("old-game", true)
            assertTrue(omok.localReady.value)
            omok.onVisible("new-activity", true); omok.onGameVisible("new-game", true)
            omok.onVisible("old-activity", false); omok.onGameVisible("old-game", false)
            assertTrue(omok.localReady.value)
            omok.onVisible("new-activity", false)
            assertFalse(omok.localReady.value)
            omok.onVisible("new-activity", true)
            assertTrue(omok.localReady.value)
            omok.onGameVisible("new-game", false)
            assertFalse(omok.localReady.value)
        } finally { scope.cancel() }
    }
    @Test fun selectionMadeBeforeBackgroundingCannotBeSubmittedAfterReturning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val (omok, transport) = fixture(scope)
            omok.onVisible("activity", true); omok.onGameVisible("game", true)
            omok.invite("b")
            val invite = omok.games.value.values.single()
            transport.presence(invite.id, "b", OmokPresence("b1", true, true, 10_000))
            transport.change { rooms, now -> rooms + (invite.id to OmokRules.accept(rooms.getValue(invite.id), "b", invite.revision, now)) }
            val playing = omok.games.value.getValue(invite.id)
            val selection = OmokSelection(playing.id, playing.revision, playing.tokens(), 84)
            omok.onVisible("activity", false); omok.onVisible("activity", true)
            omok.place(selection)
            assertTrue(omok.games.value.getValue(invite.id).moves.isEmpty())
            assertNotNull(omok.error.value)
            assertFalse(omok.pending.value)
        } finally { scope.cancel() }
    }
}
