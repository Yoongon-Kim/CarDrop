package com.cardrop.drive

import com.cardrop.core.model.DropTopic
import com.cardrop.core.demo.DemoData
import com.cardrop.vehicle.FakeVehicleRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DriveViewModelTest {
    private fun runScenario(block: suspend (DriveViewModel, TestCrew, TestNavigation, TestAi, FakeVehicleRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val crew = TestCrew()
        val nav = TestNavigation()
        val ai = TestAi()
        val vehicle = FakeVehicleRepository()
        val vm = DriveViewModel(vehicle, TestRoute(), nav, crew, ai, scope)
        try { block(vm, crew, nav, ai, vehicle) } finally { scope.cancel() }
    }
    @Test fun lowFuelRemainsPendingUntilAcknowledgedWithoutRepeatedWarnings() = runScenario { vm, _, _, _, vehicle ->
        vehicle.setFuelPercent(12)
        yield()
        assertTrue(vm.fuelWarningPending.value)
        vm.acknowledgeFuelWarning()
        vehicle.setFuelPercent(11)
        yield()
        assertFalse(vm.fuelWarningPending.value)
        vehicle.setFuelPercent(40)
        vehicle.setFuelPercent(12)
        yield()
        assertTrue(vm.fuelWarningPending.value)
    }

    // Board 05: step 1 asks the AI, step 2 sends or routes only the station the driver picked.

    @Test fun doubleTapAsksTheAiOnceAndNothingIsSentBeforeTheDriverChooses() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(30)
        ai.gate = CompletableDeferred()
        vm.planFuelStop(shared = true)
        vm.planFuelStop(shared = true)
        assertTrue(vm.action.value.busy)
        assertEquals(1, ai.requests)
        ai.gate!!.complete(Unit)
        yield()
        val plan = vm.fuelPlan.value!!
        assertTrue(plan.shared)
        assertEquals(3, plan.stations.size)
        assertFalse(vm.action.value.busy)
        assertEquals(0, crew.sends)
    }
    @Test fun onlyTheChosenStationIsSentOnceAndTheRouteWaitsForTheVote() = runScenario { vm, crew, nav, _, vehicle ->
        vehicle.setFuelPercent(30)
        vm.planFuelStop(shared = true)
        yield()
        val chosen = vm.fuelPlan.value!!.stations[1]
        var done = 0
        vm.confirmFuelStop(1) { done++ }
        vm.confirmFuelStop(1) { done++ }
        yield()
        assertEquals(1, crew.sends)
        assertEquals(1, done)
        val drop = crew.drops.value.single()
        assertEquals(listOf(chosen.candidate), drop.candidates)
        assertTrue(chosen.candidate.place.name in drop.message)
        assertNull(vm.fuelPlan.value)
        assertTrue(nav.changes.isEmpty())
    }
    @Test fun goingAloneRoutesOnlyThisCarThroughTheChosenStation() = runScenario { vm, crew, nav, _, vehicle ->
        vehicle.setFuelPercent(30)
        vm.planFuelStop(shared = false)
        yield()
        val plan = vm.fuelPlan.value!!
        assertFalse(plan.shared)
        var done = false
        vm.confirmFuelStop(plan.stations.lastIndex) { done = true }
        yield()
        assertTrue(done)
        assertEquals(0, crew.sends)
        assertEquals(1, nav.launches)
        assertEquals(plan.stations.last().candidate.place, nav.route.value.waypoint)
    }
    @Test fun cancellingGoesBackToTheQuestionWithoutSending() = runScenario { vm, crew, _, _, vehicle ->
        vehicle.setFuelPercent(12)
        vm.planFuelStop(shared = true)
        yield()
        assertNotNull(vm.fuelPlan.value)
        vm.cancelFuelPlan()
        assertNull(vm.fuelPlan.value)
        assertEquals(0, crew.sends)
    }
    @Test fun aFailedRecommendationShowsAnErrorAndCanBeRetried() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.fail = true
        vm.planFuelStop(shared = true)
        yield()
        assertNotNull(vm.action.value.error)
        assertNull(vm.fuelPlan.value)
        ai.fail = false
        vm.planFuelStop(shared = true)
        yield()
        assertNotNull(vm.fuelPlan.value)
        assertNull(vm.action.value.error)
        assertEquals(0, crew.sends)
    }
    @Test fun crewChangeDuringRecommendationShowsNoPlan() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.gate = CompletableDeferred()
        vm.planFuelStop(shared = true)
        crew.crew.value = crew.crew.value!!.copy(code = "654321")
        ai.gate!!.complete(Unit)
        yield()
        assertNull(vm.fuelPlan.value)
        assertNotNull(vm.action.value.error)
        assertEquals(0, crew.sends)
    }
    @Test fun crewChangeAfterPlanningDropsThePlan() = runScenario { vm, crew, _, _, vehicle ->
        vehicle.setFuelPercent(12)
        vm.planFuelStop(shared = true)
        yield()
        assertNotNull(vm.fuelPlan.value)
        crew.crew.value = crew.crew.value!!.copy(code = "654321")
        yield()
        assertNull(vm.fuelPlan.value)
        vm.confirmFuelStop(0) { fail("a plan made for the old crew must not be sent") }
        yield()
        assertEquals(0, crew.sends)
    }
    @Test fun fuelDroppingDuringRecommendationLeavesNoStationsToShow() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.gate = CompletableDeferred()
        vm.planFuelStop(shared = true)
        vehicle.setFuelPercent(1)
        ai.gate!!.complete(Unit)
        yield()
        assertNull(vm.fuelPlan.value)
        assertNotNull(vm.action.value.error)
        assertFalse(vm.action.value.busy)
        assertEquals(0, crew.sends)
    }
    @Test fun aStationThatBecameUnreachableWhileTheDriverChoseIsNotSent() = runScenario { vm, crew, _, _, vehicle ->
        vehicle.setFuelPercent(30)
        vm.planFuelStop(shared = true)
        yield()
        val chosen = vm.fuelPlan.value!!.stations.first()
        vehicle.setFuelPercent(1)
        vm.confirmFuelStop(0) { fail("an unreachable station must not be sent") }
        yield()
        assertEquals(0, crew.sends)
        assertNotNull(vm.action.value.error)
        assertTrue(vm.fuelPlan.value?.stations.orEmpty().none { it == chosen })
    }
    @Test fun noCandidatesOrFailedNavigationNeverReportSuccess() = runScenario { vm, crew, nav, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.empty = true
        vm.planFuelStop(shared = true)
        yield()
        assertNull(vm.fuelPlan.value)
        assertNotNull(vm.action.value.error)
        ai.empty = false
        nav.available = false
        vm.planFuelStop(shared = false)
        yield()
        var done = false
        vm.confirmFuelStop(0) { done = true }
        yield()
        assertFalse(done)
        assertNotNull(vm.action.value.error)
        assertEquals(0, crew.sends)
    }

    @Test fun joiningFuelVotesAndPutsTheStationIntoTheRouteRightAway() = runScenario { vm, crew, nav, _, _ ->
        val drop = decision("invite", DropTopic.FUEL, decided = false)
        crew.drops.value = listOf(drop)
        var done = false
        vm.joinFuelStop(drop) { done = true }
        yield()
        assertTrue(done)
        assertEquals(1, crew.votes)
        assertEquals(drop.candidates.first().place, nav.route.value.waypoint)
        assertEquals(0, nav.launches)
    }
    @Test fun guidanceWithoutDestinationDoesNotLaunch() = runScenario { vm, _, nav, _, _ ->
        assertFalse(vm.startGuidance())
        assertEquals(0, nav.launches)
        assertNotNull(vm.action.value.error)
    }
    @Test fun aPrivateFuelInvitationIsHiddenAndCannotBeJoinedByAnotherCar() = runScenario { vm, crew, _, _, _ ->
        val private = decision("private", DropTopic.FUEL, decided = false).copy(toCarIds = listOf(DemoData.aunt.id))
        crew.drops.value = listOf(private)
        yield()
        assertTrue(vm.drops.value.isEmpty())
        vm.joinFuelStop(private) { fail("must not join another car's private invitation") }
        yield()
        assertEquals(0, crew.votes)
        assertNotNull(vm.action.value.error)
    }
    @Test fun skippingAWaypointPreservesOurCarsPrivateDestination() = runScenario { vm, _, nav, _, _ ->
        val destination = DemoData.destinations[1]
        nav.setRoute(destination, DemoData.restStops.first(), "private destination")
        assertTrue(vm.skipStop("stop"))
        assertEquals(destination, nav.route.value.destination)
        assertNull(nav.route.value.waypoint)
    }
    @Test fun theCarPickedWhenJoiningCountsEvenAfterTheViewModelStarted() = runScenario { vm, crew, _, _, _ ->
        val forAunt = decision("aunt-only", decided = false).copy(toCarIds = listOf(DemoData.aunt.id))
        crew.drops.value = listOf(forAunt)
        yield()
        assertTrue(vm.drops.value.isEmpty())
        // Joining with the real crew: this device becomes 이모 차 and the crew arrives.
        crew.carId = DemoData.aunt.id
        crew.crew.value = crew.crew.value!!.copy(code = "654321", cars = listOf(DemoData.uncle, DemoData.aunt))
        yield()
        assertEquals(DemoData.aunt.id, vm.myCarId)
        assertEquals(listOf(forAunt), vm.drops.value)
    }

    @Test fun aCarThatJoinedSaysWhoItWaitsForUntilTheHostStarts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val crew = TestCrew(notStarted = true).apply { carId = DemoData.uncle.id } // the crew's host is 아빠 차
        val vm = DriveViewModel(FakeVehicleRepository(), TestRoute(), TestNavigation(), crew, TestAi(), scope)
        try {
            assertEquals(DemoData.me.name, vm.waitingForHost.value)
            crew.startTrip()
            yield()
            assertNull(vm.waitingForHost.value)
        } finally { scope.cancel() }
    }

    @Test fun theHostNeverWaits() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = DriveViewModel(FakeVehicleRepository(), TestRoute(), TestNavigation(), TestCrew(notStarted = true), TestAi(), scope)
        try { assertNull(vm.waitingForHost.value) } finally { scope.cancel() }
    }

    // Board 04's crew cards: this car's fuel from its own car, the other cars' as they shared it.

    @Test fun crewCardsShowThisCarsFuelAndTheOtherCarsShared() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vehicle = FakeVehicleRepository()
        val others = object : CrewFuel {
            override val fuel = MutableStateFlow(mapOf(DemoData.uncle.id to CarFuel(48, 312)))
        }
        val vm = DriveViewModel(vehicle, TestRoute(), TestNavigation(), TestCrew(), TestAi(), scope, crewFuel = others)
        try {
            assertEquals(mapOf(DemoData.me.id to CarFuel(64, 420), DemoData.uncle.id to CarFuel(48, 312)), vm.fuelByCar.value)
            vehicle.setFuelPercent(12)
            others.fuel.value = mapOf(DemoData.uncle.id to CarFuel(15, 98))
            yield()
            assertEquals(mapOf(DemoData.me.id to CarFuel(12, 78), DemoData.uncle.id to CarFuel(15, 98)), vm.fuelByCar.value)
        } finally { scope.cancel() }
    }
}
