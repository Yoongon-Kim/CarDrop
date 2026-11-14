package com.cardrop.core.demo

import com.cardrop.crew.FirebaseCrewRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarNamesTest {
    @Test fun aTypedNameIsTrimmedAndItsSpacesSqueezed() {
        assertEquals("윤곤 차", CarNames.clean("  윤곤   차 "))
    }

    @Test fun aLongNameIsCutAtTheLimit() {
        assertEquals("가나다라마바사아자차", CarNames.clean("가나다라마바사아자차카타"))
        assertEquals(CarNames.MAX_LENGTH, CarNames.clean("가".repeat(30))!!.length)
    }

    @Test fun aCutNameDoesNotEndInASpace() {
        assertEquals("가나다라마바사아자", CarNames.clean("가나다라마바사아자 차"))
    }

    @Test fun bracketsAreDropped() {
        // The app adds " (나)" to this car's name and cuts names at " (", so a typed bracket would confuse both.
        assertEquals("큰형 차", CarNames.clean("(큰형) 차"))
    }

    @Test fun nothingLeftMeansTheDesignsName() {
        assertNull(CarNames.clean(""))
        assertNull(CarNames.clean("   "))
        assertNull(CarNames.clean("()"))
    }

    @Test fun aCarNeverRenamedHasTheDesignsName() {
        assertEquals("아빠 차", CarNames.of("car-dad"))
        assertEquals("이모 차", CarNames.of("car-aunt"))
        assertEquals("아빠 차 (나)", DemoData.me.name)
        assertEquals("민지 차", DemoData.minji.name)
    }

    @Test fun everyDemoCarHasADefaultName() {
        for (key in DemoCar.KEYS) {
            val car = FirebaseCrewRepository.carFor(key)
            assertEquals(key, CarNames.DEFAULTS[car.id], car.name)
        }
    }
}
