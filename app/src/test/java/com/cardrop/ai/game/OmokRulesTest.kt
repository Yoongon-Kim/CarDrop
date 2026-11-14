package com.cardrop.ai.game

import com.cardrop.core.model.Gear
import com.cardrop.core.model.VehicleSnapshot
import org.junit.Assert.*
import org.junit.Test

class OmokRulesTest {
    private val now = 10_000L
    private val ready = mapOf("a" to OmokPresence("a1", true, true, now), "b" to OmokPresence("b1", true, true, now))
    private fun game(moves: List<Int> = emptyList()) = OmokGame("g", "a", "b", now, OmokPhase.PLAYING, moves = moves, presence = ready)
    private fun reject(messagePart: String = "", block: () -> Unit) {
        try { block(); fail("Expected command rejection") }
        catch (e: OmokRejected) { assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(messagePart)) }
    }
    private fun move(g: OmokGame, cell: Int, actor: String = g.turnId) = OmokRules.move(g, actor, cell, g.revision, g.tokens(), now)

    @Test fun blackStartsAndTurnsAlternateWithoutMutatingOldBoard() {
        val original = game()
        val first = move(original, 84)
        val second = move(first, 85)
        assertEquals("a", original.turnId); assertEquals("b", first.turnId); assertEquals("a", second.turnId)
        assertEquals(0, original.board()[84]); assertEquals(1, first.board()[84]); assertEquals(2, second.board()[85])
        assertEquals(2, second.revision)
    }
    @Test fun cannotPlaceOnOccupiedOrOutOfBoundsCellOrTakeOpponentsTurn() {
        val g = move(game(), 84)
        reject { move(g, 84) }; reject { move(game(), -1) }; reject { move(game(), 169) }; reject { move(game(), 0, "b") }
        reject { move(game(), 0, "stranger") }
    }
    @Test fun winsInAllFourDirectionsAndCannotContinueAfterWin() {
        val lines = listOf(listOf(0, 1, 2, 3, 4), listOf(0, 13, 26, 39, 52), listOf(0, 14, 28, 42, 56), listOf(12, 24, 36, 48, 60))
        for (line in lines) {
            var g = game()
            line.forEachIndexed { i, cell ->
                g = move(g, cell)
                if (i < 4) g = move(g, 150 + i * 2)
            }
            assertEquals(OmokPhase.FINISHED, g.phase)
            assertEquals("a", g.winnerId)
            assertEquals(line.toSet(), OmokRules.winningLine(g).toSet())
            reject { move(g, 80) }
        }
    }
    @Test fun sixConnectedWinsAndRowsDoNotWrap() {
        val six = game(listOf(0, 130, 1, 132, 2, 134, 4, 136, 5, 138))
        assertEquals("a", move(six, 3).winnerId)
        val wrapped = game(listOf(11, 130, 12, 132, 13, 134, 14, 136))
        assertEquals(OmokPhase.PLAYING, move(wrapped, 15).phase)
    }
    @Test fun whiteCanWin() {
        var g = game()
        repeat(5) { i -> g = move(g, i * 2); g = move(g, 26 + i) }
        assertEquals("b", g.winnerId)
    }
    @Test fun fullBoardWithoutFiveIsADraw() {
        // This periodic pattern has no run longer than two in any of the four directions.
        val black = (0 until OMOK_CELLS).filter { (it / OMOK_SIZE + 2 * (it % OMOK_SIZE)) % 4 < 2 }
        val white = (0 until OMOK_CELLS).filter { it !in black }
        assertEquals(85, black.size); assertEquals(84, white.size)
        var g = game()
        repeat(white.size) { i -> g = move(g, black[i]); g = move(g, white[i]) }
        g = move(g, black.last())
        assertEquals(OmokPhase.FINISHED, g.phase)
        assertNull(g.winnerId)
        assertEquals("무승부", g.reason)
    }
    @Test fun staleRevisionRejectsDoubleTapAndDelayedNetworkMove() {
        val g = game()
        val first = move(g, 84)
        reject("갱신") { OmokRules.move(first, "a", 85, g.revision, g.tokens(), now) }
    }
    @Test fun bothPlayersNeedFreshConnectedReadyLeases() {
        val g = game()
        for (p in listOf(ready.getValue("b").copy(ready = false), ready.getValue("b").copy(connected = false), ready.getValue("b").copy(at = now - OMOK_PRESENCE_MS - 1), ready.getValue("b").copy(at = now + 3_000))) {
            reject { move(g.copy(presence = ready + ("b" to p)), 0) }
        }
        reject { move(g.copy(presence = ready - "b"), 0) }
    }
    @Test fun newConnectionOrParkEpochInvalidatesTheOldSelectionEvenIfBoardDidNotChange() {
        val before = game()
        val after = before.copy(presence = ready + ("b" to ready.getValue("b").copy(token = "b2")))
        reject("상태") { OmokRules.move(after, "a", 84, before.revision, before.tokens(), now) }
        assertEquals(1, move(after, 84).moves.size)
    }
    @Test fun onlyReceiverCanAcceptAndInvitationExpiresAtTheDeadline() {
        val g = game().copy(phase = OmokPhase.INVITED)
        reject { OmokRules.accept(g, "a", 0, now) }
        reject { OmokRules.accept(g, "stranger", 0, now) }
        reject { OmokRules.accept(g, "b", 0, now + OMOK_INVITE_MS) }
        assertEquals(OmokPhase.PLAYING, OmokRules.accept(g, "b", 0, now).phase)
        reject { OmokRules.accept(g.copy(presence = emptyMap()), "b", 0, now) }
    }
    @Test fun busyPlayersCannotReceiveAnotherInvitationButExpiredInvitesDoNotBlock() {
        val g = game().copy(phase = OmokPhase.INVITED)
        val other = OmokGame("other", "c", "b", now, presence = mapOf("c" to OmokPresence("c", true, true, now)))
        reject { OmokRules.invite(mapOf(g.id to g), other, now) }
        val later = now + OMOK_INVITE_MS
        val fresh = other.copy(presence = mapOf("c" to OmokPresence("c", true, true, later)))
        assertEquals(2, OmokRules.invite(mapOf(g.id to g), fresh, later).size)
    }
    @Test fun unrelatedPairCanPlayWhileAnotherPairPlays() {
        val next = OmokGame("next", "c", "d", now, presence = mapOf("c" to OmokPresence("c", true, true, now)))
        assertEquals(2, OmokRules.invite(mapOf("g" to game()), next, now).size)
    }
    @Test fun cancelDeclineAndResignHaveDifferentResults() {
        val invited = game().copy(phase = OmokPhase.INVITED)
        assertEquals(OmokPhase.CANCELLED, OmokRules.close(invited, "a", 0, now).phase)
        assertEquals(OmokPhase.DECLINED, OmokRules.close(invited, "b", 0, now).phase)
        assertEquals(OmokPhase.EXPIRED, OmokRules.close(invited, "b", 0, now + OMOK_INVITE_MS).phase)
        assertEquals("b", OmokRules.close(game(), "a", 0, now).winnerId)
        reject { OmokRules.close(game(), "stranger", 0, now) }
    }
    @Test fun realGearMustHaveARecentMeasurementAndDemoGearStillRequiresPark() {
        assertTrue(omokGearReady(VehicleSnapshot(gear = Gear.PARK, gearCheckedAtElapsedMillis = 100), 200, false))
        assertFalse(omokGearReady(VehicleSnapshot(gear = Gear.PARK), 200, false))
        assertFalse(omokGearReady(VehicleSnapshot(gear = Gear.PARK, gearCheckedAtElapsedMillis = 100), 7_000, false))
        assertFalse(omokGearReady(VehicleSnapshot(gear = Gear.PARK, gearCheckedAtElapsedMillis = 300), 200, false))
        for (gear in listOf(Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL, Gear.UNKNOWN)) {
            assertFalse(omokGearReady(VehicleSnapshot(gear = gear, gearCheckedAtElapsedMillis = 100), 200, false))
            assertFalse(omokGearReady(VehicleSnapshot(gear = gear), 200, true))
        }
        assertTrue(omokGearReady(VehicleSnapshot(gear = Gear.PARK), 200, true))
    }
    @Test fun practiceBotWinsOrBlocksAnImmediateWin() {
        val attack = game(listOf(0, 130, 1, 132, 2, 134, 3, 136))
        assertEquals(4, OmokBot.choose(attack))
        val defense = game(listOf(0, 130, 1, 132, 2, 134, 3))
        assertEquals(4, OmokBot.choose(defense))
        assertEquals(84, OmokBot.choose(game()))
    }
}
