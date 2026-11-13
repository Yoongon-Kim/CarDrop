package com.cardrop.navi

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real crew: this car from its own GPS, the others where their devices said (GpsCrewPositions). */
class WithSharedCarsTest {
    private fun at(id: String, km: Float) = CarPosition(id, 37.3, 127.5, km, 100)
    private val ids = listOf(DemoData.me.id, DemoData.uncle.id)

    @Test fun thisCarFromItsGpsTheOtherFromItsDevice() {
        val mine = at(DemoData.me.id, 150f)
        val shared = mapOf(
            DemoData.me.id to at(DemoData.me.id, 999f), // an old copy of this car never wins over its GPS
            DemoData.uncle.id to at(DemoData.uncle.id, 151f),
            "car-not-in-crew" to at("car-not-in-crew", 10f),
        )
        val all = withSharedCars(DemoData.me.id, mine, ids, shared)
        assertEquals(setOf(DemoData.me.id, DemoData.uncle.id), all.keys)
        assertEquals(150f, all.getValue(DemoData.me.id).kmToDestination)
        assertEquals(151f, all.getValue(DemoData.uncle.id).kmToDestination)
    }

    @Test fun beforeTheFirstFixTheOtherCarsStillShow() {
        val all = withSharedCars(DemoData.me.id, mine = null, ids, mapOf(DemoData.uncle.id to at(DemoData.uncle.id, 151f)))
        assertEquals(setOf(DemoData.uncle.id), all.keys)
    }
}
