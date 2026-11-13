package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 04's fuel on every crew card (drive/CrewFuel.kt) and how it travels through the trip (drive/FirebaseTrip.kt). */
class CrewFuelTest {
    @Test fun thisCarsFuelFromWhatItReports() {
        assertEquals(CarFuel(64, 420), VehicleSnapshot(fuelPercent = 64, rangeKm = 420f).carFuel())
        assertEquals(CarFuel(12, 78), VehicleSnapshot(fuelPercent = 12, rangeKm = 78.4f).carFuel())
        assertEquals(CarFuel(30), VehicleSnapshot(fuelPercent = 30, rangeKm = Float.NaN).carFuel())
        assertNull(VehicleSnapshot(fuelPercent = null, rangeKm = 300f).carFuel())
        assertNull(VehicleSnapshot(fuelPercent = 140).carFuel())
    }

    @Test fun underTwentyPercentIsLow() {
        assertTrue(CarFuel(19).isLow)
        assertFalse(CarFuel(20).isLow)
    }

    @Test fun cardsShowThisCarsOwnFuelAndTheConnectedCarsShared() {
        val cars = listOf(DemoData.me, DemoData.uncle, DemoData.minji.copy(connected = false), DemoData.aunt)
        val shared = mapOf(
            DemoData.me.id to CarFuel(90, 600), // what this car shared earlier: its own car says it better
            DemoData.uncle.id to CarFuel(48, 312),
            DemoData.minji.id to CarFuel(12, 85), // its app was closed: the last value stays behind
            "car-other" to CarFuel(50), // not in this crew
        )
        assertEquals(
            mapOf(DemoData.me.id to CarFuel(64, 420), DemoData.uncle.id to CarFuel(48, 312)),
            crewCardFuel(cars, DemoData.me.id, mine = CarFuel(64, 420), others = shared),
        )
        assertEquals(
            mapOf(DemoData.uncle.id to CarFuel(48, 312)),
            crewCardFuel(cars, DemoData.me.id, mine = null, others = shared),
        )
    }

    @Test fun botsShowTheDesignsFuelUntilOneSendsAFuelWarning() {
        val start = botFuel(emptyList())
        assertEquals(CarFuel(48, 312), start[DemoData.uncle.id])
        assertEquals(CarFuel(55, 358), start[DemoData.aunt.id])
        assertFalse(start.values.any { it.isLow })

        val warned = botFuel(
            listOf(
                decision("minji-fuel", topic = DropTopic.FUEL, from = DemoData.minji.id, decided = false),
                decision("my-fuel", topic = DropTopic.FUEL, from = DemoData.me.id, decided = false),
                decision("uncle-rest", topic = DropTopic.REST, from = DemoData.uncle.id),
            ),
        )
        assertEquals(CarFuel(12, 85), warned[DemoData.minji.id])
        assertEquals(start - DemoData.minji.id, warned - DemoData.minji.id)
    }

    @Test fun sharedFuelReadsBackTheSame() {
        listOf(CarFuel(64, 420), CarFuel(12, 85), CarFuel(0, 0), CarFuel(100)).forEach { fuel ->
            val written = fuelToShare(fuel)
            // Firebase hands whole numbers back as Long.
            assertEquals(fuel, parseCarFuel(written["pct"]?.toLong(), written["km"]?.toLong()))
        }
        assertEquals(setOf("pct"), fuelToShare(CarFuel(30)).keys)
    }

    @Test fun unusableSharedFuelIsIgnored() {
        assertNull(parseCarFuel(null, 300L))
        assertNull(parseCarFuel("48", 300L))
        assertNull(parseCarFuel(140L, 300L))
        assertEquals(CarFuel(48), parseCarFuel(48L, -5L))
        assertEquals(CarFuel(48, 312), parseCarFuel(48.0, 312.4))
    }
}
