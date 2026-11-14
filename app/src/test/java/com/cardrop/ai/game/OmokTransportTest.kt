package com.cardrop.ai.game

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OmokTransportTest {
    private class Store : OmokRoomStore {
        val data = mutableMapOf<String, String>()
        override fun read(key: String): String? = data[key]
        override fun write(key: String, value: String) { data[key] = value }
    }
    private val now = 10_000L
    private fun ready() = mapOf("a" to OmokPresence("a", true, true, now), "b" to OmokPresence("b", true, true, now))
    private fun game() = OmokGame("g", "a", "b", now, presence = ready())

    @Test fun twoPlayersShareAcceptMovePauseAndFreshSelectionAfterResume() = runBlocking {
        val transport = LocalOmokTransport(Store()) { now }
        transport.follow("crew")
        transport.change { rooms, time -> OmokRules.invite(rooms, game(), time) }
        transport.change { rooms, time -> rooms + ("g" to OmokRules.accept(rooms.getValue("g"), "b", 0, time)) }
        val selected = transport.games.value.getValue("g")
        transport.presence("g", "b", ready().getValue("b").copy(ready = false))
        try {
            transport.change { rooms, time -> rooms + ("g" to OmokRules.move(rooms.getValue("g"), "a", 84, selected.revision, selected.tokens(), time)) }
            fail("Should pause")
        } catch (_: OmokRejected) { }
        transport.presence("g", "b", ready().getValue("b").copy(token = "b2"))
        val fresh = transport.games.value.getValue("g")
        transport.change { rooms, time -> rooms + ("g" to OmokRules.move(rooms.getValue("g"), "a", 84, fresh.revision, fresh.tokens(), time)) }
        assertEquals(listOf(84), transport.games.value.getValue("g").moves)
    }
    @Test fun confirmedBoardRestoresButOldParkAndConnectionLeasesDoNot() = runBlocking {
        val store = Store()
        val first = LocalOmokTransport(store) { now }
        first.follow("crew")
        val playing = game().copy(phase = OmokPhase.PLAYING, moves = listOf(84, 85), revision = 3)
        first.change { _, _ -> mapOf("g" to playing) }
        val restarted = LocalOmokTransport(store) { now }
        restarted.follow("crew")
        assertEquals(playing.moves, restarted.games.value.getValue("g").moves)
        assertEquals(3, restarted.games.value.getValue("g").revision)
        assertFalse(restarted.games.value.getValue("g").ready(now))
        assertTrue(restarted.games.value.getValue("g").presence.isEmpty())
        restarted.follow("another-crew")
        assertTrue(restarted.games.value.isEmpty())
    }
    @Test fun roundTripHandlesFirebaseLongNumbersAndMalformedRoomDoesNotCrashOtherRooms() {
        val original = game().copy(phase = OmokPhase.PLAYING, revision = 3, moves = listOf(84, 85))
        val encoded = OmokCodec.encode(original).toMutableMap()
        encoded["revision"] = 3L
        assertEquals(original, OmokCodec.decode(encoded))
        assertEquals(original, OmokCodec.fromJson(OmokCodec.toJson(mapOf("g" to original)))["g"])
        val invalid = encoded + ("moves" to "84,84")
        assertNull(OmokCodec.decode(invalid))
        assertNull(OmokCodec.decode(encoded + ("moves" to "169")))
        assertNull(OmokCodec.decode(encoded + ("phase" to "NEW_UNKNOWN_FORMAT")))
        assertEquals(setOf("g"), OmokCodec.decodeRooms(mapOf("g" to encoded, "bad" to invalid)).keys)
        assertTrue(OmokCodec.fromJson("bad json").isEmpty())
    }
    @Test fun rejectedOperationLeavesPersistedAndVisibleBoardIntact() = runBlocking {
        val store = Store()
        val transport = LocalOmokTransport(store) { now }
        transport.follow("crew")
        transport.change { _, _ -> mapOf("g" to game().copy(phase = OmokPhase.PLAYING)) }
        val saved = store.data["crew"]
        try { transport.change { _, _ -> throw OmokRejected("rejected") }; fail() } catch (_: OmokRejected) { }
        assertEquals(saved, store.data["crew"])
        assertTrue(transport.games.value.getValue("g").moves.isEmpty())
    }
    @Test fun failedDiskWriteDoesNotPublishAnUnrecoverableConfirmedMove() = runBlocking {
        val store = object : OmokRoomStore {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String) { throw OmokRejected("disk full") }
        }
        val transport = LocalOmokTransport(store) { now }
        transport.follow("crew")
        try { transport.change { _, _ -> mapOf("g" to game()) }; fail() } catch (_: OmokRejected) { }
        assertTrue(transport.games.value.isEmpty())
    }
}
