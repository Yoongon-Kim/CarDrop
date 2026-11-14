package com.cardrop.safety

import org.junit.Assert.*
import org.junit.Test

class SafetyEntryGateTest {
    @Test fun onlyExplicitSlotEntryCanRequestOneGreeting() {
        val gate = SafetyEntryGate()
        assertFalse(gate.enter(null, true, 0, 0)) // rail, result, arrival
        val slot = gate.newEntry()
        assertTrue(gate.enter(slot, true, 0, 0))
        assertTrue(gate.valid(slot, 0, 100))
        assertFalse(gate.enter(slot, true, 0, 200)) // recreation / restoration
        gate.leave()
        assertFalse(gate.enter(slot, true, 0, 300)) // back / foreground
        assertTrue(gate.enter(gate.newEntry(), true, 0, 400)) // explicit slot re-entry
    }

    @Test fun leaveNewPenaltyAndLateResponseInvalidateThisEntry() {
        val gate = SafetyEntryGate()
        val slot = gate.newEntry()
        gate.enter(slot, true, 3, 100)
        assertFalse(gate.valid(slot, 4, 200))
        assertFalse(gate.valid(slot, 3, 5101))
        gate.leave()
        assertFalse(gate.valid(slot, 3, 200))
    }

    @Test fun turningOnAfterAnEntryDoesNotReplayAndNewEntryInvalidatesOldResponse() {
        val gate = SafetyEntryGate()
        val muted = gate.newEntry()
        assertFalse(gate.enter(muted, false, null, 0))
        assertFalse(gate.enter(muted, true, null, 100))
        val first = gate.newEntry(); gate.enter(first, true, 0, 200)
        val next = gate.newEntry(); gate.enter(next, true, 0, 300)
        assertFalse(gate.valid(first, 0, 400)); assertTrue(gate.valid(next, 0, 400))
    }
}
