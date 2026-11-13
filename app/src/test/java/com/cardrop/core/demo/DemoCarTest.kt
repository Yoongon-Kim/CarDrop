package com.cardrop.core.demo

import com.cardrop.core.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoCarTest {
    @Test fun aPickedCarWinsOverTheBuildDefault() {
        assertEquals("minji", DemoCar.resolve(picked = "minji", buildDefault = "dad"))
    }

    @Test fun noPickOrAnUnknownOneUsesTheBuildDefault() {
        assertEquals("uncle", DemoCar.resolve(picked = null, buildDefault = "uncle"))
        assertEquals("uncle", DemoCar.resolve(picked = "ant", buildDefault = "uncle"))
    }

    @Test fun withTheRealCrewOnlyTheHostStartsOnBoard01() {
        assertEquals(Screen.CreateCrew, DemoCar.startScreen(useRealCrew = true, car = "dad"))
        for (car in listOf("uncle", "minji", "aunt")) {
            assertEquals(car, Screen.JoinCrew, DemoCar.startScreen(useRealCrew = true, car = car))
        }
    }

    @Test fun theBotCrewAlwaysStartsOnBoard01() {
        for (car in DemoCar.KEYS) assertEquals(car, Screen.CreateCrew, DemoCar.startScreen(useRealCrew = false, car = car))
    }
}
