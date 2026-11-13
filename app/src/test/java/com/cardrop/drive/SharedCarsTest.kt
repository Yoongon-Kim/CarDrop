package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Crew
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real crew: only the other cars whose device is connected are drawn (drive/FirebaseTrip.kt). */
class SharedCarsTest {
    private fun at(id: String) = CarPosition(id, 37.3, 127.5, 150f, 100)
    private val shared = listOf(DemoData.me.id, DemoData.uncle.id, DemoData.minji.id).associateWith(::at)

    @Test fun connectedOtherCarsOnly() {
        val crew = Crew(
            "482917", "test", DemoData.gyeongpo, DemoData.me.id,
            listOf(
                DemoData.me.copy(connected = true),
                DemoData.uncle.copy(connected = true),
                DemoData.minji.copy(connected = false), // its app was closed: its last position stays behind
                DemoData.aunt.copy(connected = true), // connected but nothing shared yet
            ),
        )
        assertEquals(setOf(DemoData.uncle.id), sharedCarsToShow(shared, crew, myCarId = DemoData.me.id).keys)
    }

    @Test fun noCrewNoCars() {
        assertEquals(emptyMap<String, CarPosition>(), sharedCarsToShow(shared, crew = null, myCarId = DemoData.me.id))
    }
}
