package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import com.cardrop.drive.TestCrew
import org.junit.Assert.assertEquals
import org.junit.Test

/** Boards 07 and 11 use CrewViewModel.myCarId to find this car's drops and votes. */
class CrewViewModelTest {

    @Test
    fun theCarPickedWhenJoiningCountsEvenAfterTheViewModelStarted() {
        val crew = TestCrew()
        val vm = CrewViewModel(crew)
        assertEquals(DemoData.me.id, vm.myCarId)

        // Joining with the real crew (board 03): this device becomes 이모 차 after the screen exists.
        crew.carId = DemoData.aunt.id

        assertEquals(DemoData.aunt.id, vm.myCarId)
    }
}
