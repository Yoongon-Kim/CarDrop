package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.DropTopic
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TripCoordinatorTest {
    private fun runScenario(block: suspend (CoroutineScope, TestCrew, TestNavigation, TestRoute, TestVoice, TripCoordinator) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val crew = TestCrew()
        val nav = TestNavigation()
        val route = TestRoute()
        val voice = TestVoice()
        val coordinator = TripCoordinator(scope, crew, route, nav, voice)
        try { coordinator.start(); block(scope, crew, nav, route, voice, coordinator) } finally { scope.cancel() }
    }

    @Test fun aFinishedVoteReroutesOnceWithoutLaunchingAnExternalApp() = runScenario { _, crew, nav, _, voice, coordinator ->
        val drop = decision("stop")
        crew.drops.value = listOf(drop)
        yield()
        assertEquals(drop.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
        coordinator.start()
        crew.drops.value = listOf(drop.copy(message = "sync update"))
        yield()
        assertEquals(1, voice.spoken.size)
        assertEquals(0, nav.launches)
    }
    @Test fun fuelInvitationsRerouteOnlyExplicitParticipantsAndTheSender() = runScenario { _, crew, nav, _, voice, _ ->
        crew.drops.value = listOf(decision("ignored", DropTopic.FUEL))
        yield()
        assertNull(nav.route.value.waypoint)
        assertTrue(voice.spoken.isEmpty())
        val joined = decision("joined", DropTopic.FUEL, at = 2, voted = true)
        crew.drops.value += joined
        yield()
        assertEquals(joined.decidedCandidate!!.place, nav.route.value.waypoint)
        val own = decision("own", DropTopic.FUEL, from = crew.myCarId, at = 3)
        crew.drops.value += own
        yield()
        assertEquals(2, voice.spoken.size)
    }
    @Test fun declinedStopIsNotReappliedOnSync() = runScenario { _, crew, nav, _, voice, _ ->
        nav.decisions.skip("skipped")
        crew.drops.value = listOf(decision("skipped", DropTopic.FUEL, voted = true))
        yield()
        assertNull(nav.route.value.waypoint)
        assertTrue(voice.spoken.isEmpty())
    }
    @Test fun historySyncAppliesNewestDecisionAndLateOlderResultsCannotReplaceIt() = runScenario { _, crew, nav, _, voice, _ ->
        val latest = decision("new", at = 10)
        crew.drops.value = listOf(decision("old", at = 1), latest)
        yield()
        assertEquals(latest.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
        crew.drops.value += decision("late", at = 5)
        yield()
        assertEquals(latest.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
    }
    @Test fun leavingCrewClearsItsRouteAndLocalChoices() = runScenario { _, crew, nav, route, _, _ ->
        crew.drops.value = listOf(decision("stop"))
        yield()
        nav.decisions.skip("stop")
        crew.leaveCrew()
        yield()
        assertNull(nav.route.value.destination)
        assertFalse(nav.decisions.isSkipped("stop"))
        assertTrue(route.stops > 0)
    }
    @Test fun simultaneousDecisionsHaveTheSameWinnerRegardlessOfHistoryOrder() = runScenario { _, crew, nav, _, voice, _ ->
        val earlierId = decision("new", at = 10)
        val laterId = decision("z", at = 10)
        crew.drops.value = listOf(earlierId, laterId)
        yield()
        assertEquals(laterId.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
    }
    @Test fun aLateDecisionWithTheSameTimestampCannotReplaceTheChosenWinner() = runScenario { _, crew, nav, _, voice, _ ->
        val latest = decision("z", at = 10)
        crew.drops.value = listOf(latest)
        yield()
        crew.drops.value += decision("new", at = 10)
        yield()
        assertEquals(latest.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
    }
    @Test fun aPrivateDecisionForAnotherCarCannotChangeOurRouteOrAnnounceIt() = runScenario { _, crew, nav, _, voice, _ ->
        crew.crew.value = crew.crew.value!!.copy(cars = crew.crew.value!!.cars + DemoData.aunt)
        val shared = decision("shared", at = 1)
        val private = decision("new", at = 2).copy(toCarIds = listOf(DemoData.aunt.id))
        crew.drops.value = listOf(shared, private)
        yield()
        assertEquals(shared.decidedCandidate!!.place, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
    }
    @Test fun aPrivateDestinationDecisionChangesOnlyTheParticipatingCarsRoute() = runScenario { _, crew, nav, _, _, _ ->
        crew.crew.value = crew.crew.value!!.copy(cars = crew.crew.value!!.cars + DemoData.aunt)
        val sharedDestination = crew.crew.value!!.destination
        val private = decision("private-destination", DropTopic.DESTINATION, from = crew.myCarId)
            .copy(toCarIds = listOf(DemoData.aunt.id))
        crew.drops.value = listOf(private)
        yield()
        assertEquals(sharedDestination, crew.crew.value!!.destination)
        assertEquals(private.decidedCandidate!!.place, nav.route.value.destination)
        assertNull(nav.route.value.waypoint)
    }
    @Test fun aCrewWideDestinationDecisionStillUpdatesTheSharedDestination() = runScenario { _, crew, nav, _, _, _ ->
        val shared = decision("shared-destination", DropTopic.DESTINATION)
        crew.drops.value = listOf(shared)
        yield()
        assertEquals(shared.decidedCandidate!!.place, crew.crew.value!!.destination)
        assertEquals(shared.decidedCandidate!!.place, nav.route.value.destination)
    }
}
