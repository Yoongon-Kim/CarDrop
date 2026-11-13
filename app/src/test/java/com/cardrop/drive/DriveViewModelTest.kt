package com.cardrop.drive

import com.cardrop.core.model.DropTopic
import com.cardrop.core.demo.DemoData
import com.cardrop.vehicle.FakeVehicleRepository
import kotlinx.coroutines.*
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
    @Test fun doubleTapSendsOnlyOneFuelDropAndFailuresCanBeRetried() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.gate = CompletableDeferred()
        var callbacks = 0
        vm.alertCrewAboutFuel { callbacks++ }
        vm.alertCrewAboutFuel { callbacks++ }
        assertTrue(vm.action.value.busy)
        assertEquals(1, ai.requests)
        ai.gate!!.complete(Unit)
        yield()
        assertEquals(1, crew.sends)
        assertEquals(1, callbacks)
        assertFalse(vm.action.value.busy)
        ai.fail = true
        vm.alertCrewAboutFuel { callbacks++ }
        yield()
        assertNotNull(vm.action.value.error)
        assertEquals(1, crew.sends)
        ai.fail = false
        vm.alertCrewAboutFuel { callbacks++ }
        yield()
        assertEquals(2, crew.sends)
        assertNull(vm.action.value.error)
    }
    @Test fun crewChangeDuringRecommendationDoesNotSendToTheNewCrew() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.gate = CompletableDeferred()
        vm.alertCrewAboutFuel {}
        crew.crew.value = crew.crew.value!!.copy(code = "654321")
        ai.gate!!.complete(Unit)
        yield()
        assertEquals(0, crew.sends)
        assertNotNull(vm.action.value.error)
    }
    @Test fun joiningFuelOnlyVotesAndDoesNotLaunchOrReroute() = runScenario { vm, crew, nav, _, _ ->
        val drop = decision("invite", DropTopic.FUEL, decided = false)
        crew.drops.value = listOf(drop)
        var done = false
        vm.joinFuelStop(drop) { done = true }
        yield()
        assertTrue(done)
        assertEquals(1, crew.votes)
        assertEquals(0, nav.launches)
        assertTrue(nav.changes.isEmpty())
    }
    @Test fun fuelDroppingDuringRecommendationInvalidatesTheOldCandidates() = runScenario { vm, crew, _, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.gate = CompletableDeferred()
        vm.alertCrewAboutFuel { fail("outdated fuel candidates must not be sent") }
        vehicle.setFuelPercent(1)
        ai.gate!!.complete(Unit)
        yield()
        assertEquals(0, crew.sends)
        assertNotNull(vm.action.value.error)
        assertFalse(vm.action.value.busy)
    }
    @Test fun noCandidatesOrFailedNavigationNeverReportSuccess() = runScenario { vm, crew, nav, ai, vehicle ->
        vehicle.setFuelPercent(12)
        ai.empty = true
        vm.alertCrewAboutFuel { fail("must not send") }
        yield()
        assertEquals(0, crew.sends)
        assertNotNull(vm.action.value.error)
        ai.empty = false
        nav.available = false
        var done = false
        vm.goToStationAlone { done = true }
        yield()
        assertFalse(done)
        assertNotNull(vm.action.value.error)
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
}
