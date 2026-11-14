package com.cardrop.crew

import com.cardrop.crew.SeatRules.Pick
import com.cardrop.crew.SeatRules.Seat
import org.junit.Assert.assertEquals
import org.junit.Test

/** Board 03: which seat a car gets when it joins with the code. */
class SeatRulesTest {
    private fun free(id: String) = Seat(id, device = null, connected = false, out = false)

    @Test
    fun takesTheFirstFreeSeat() {
        val seats = listOf(Seat("uncle", "other", connected = true, out = false), free("minji"), free("aunt"))
        assertEquals(Pick.Take("minji"), SeatRules.pick(seats, "me"))
    }

    @Test
    fun getsItsOwnSeatBackAfterARestart() {
        val seats = listOf(free("uncle"), Seat("minji", "me", connected = false, out = false), free("aunt"))
        assertEquals(Pick.Take("minji"), SeatRules.pick(seats, "me"))
    }

    @Test
    fun aFullCrewTakesNobodyElse() {
        val seats = listOf("uncle", "minji", "aunt").map { Seat(it, "d-$it", connected = true, out = false) }
        assertEquals(Pick.Full, SeatRules.pick(seats, "me"))
    }

    @Test
    fun aStartedCrewTakesNobodyNew() {
        // At the start the free seats were left out ("out"), like a car that hadn't come.
        val seats = listOf(Seat("uncle", "other", connected = true, out = false), free("minji").copy(out = true), free("aunt").copy(out = true))
        assertEquals(Pick.Closed, SeatRules.pick(seats, "me"))
        // A car that was in still gets back in; one that was left out doesn't.
        assertEquals(Pick.Take("uncle"), SeatRules.pick(seats.map { if (it.id == "uncle") it.copy(device = "me", connected = false) else it }, "me"))
        assertEquals(Pick.Closed, SeatRules.pick(seats.map { if (it.id == "minji") it.copy(device = "me") else it }, "me"))
    }
}
