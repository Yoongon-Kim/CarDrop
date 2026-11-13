package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.navi.WaypointEvent
import com.cardrop.navi.WaypointResult
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

    @Test fun stoppingAtTheStopIsSpokenAndToldToTheCrew() = runScenario { _, crew, nav, _, voice, _ ->
        nav.waypointEvents.emit(WaypointEvent(DemoData.restStops[1], WaypointResult.ARRIVED))
        yield()
        assertEquals("여주휴게소에 도착했어요", voice.spoken.last())
        val sent = crew.drops.value.single()
        assertEquals(DropKind.SOCIAL, sent.kind) // on screen only, never spoken on the other cars
        assertEquals("여주휴게소에 도착했어요", sent.title)
        assertTrue(sent.toCarIds.isEmpty()) // the whole crew
    }

    @Test fun theStopCardFollowsGettingThereThenStopping() = runScenario { _, crew, nav, _, _, coordinator ->
        val stop = DemoData.restStops[1]
        nav.reachedWaypoint.emit(stop)
        yield()
        assertEquals(StopNotice.Kind.AT_STOP, coordinator.notice.value?.kind)
        assertEquals(stop, coordinator.notice.value?.place)
        nav.waypointEvents.emit(WaypointEvent(stop, WaypointResult.ARRIVED))
        yield()
        assertEquals(StopNotice.Kind.ARRIVED, coordinator.notice.value?.kind)
        assertTrue(coordinator.notice.value!!.crewTold) // shown after the crew message went out
        crew.leaveCrew()
        yield()
        assertNull(coordinator.notice.value) // a new crew starts without it
    }

    @Test fun drivingPastTheStopIsSpokenAndToldToTheCrew() = runScenario { _, crew, nav, _, voice, _ ->
        nav.waypointEvents.emit(WaypointEvent(DemoData.restStops[1], WaypointResult.PASSED))
        yield()
        assertEquals("여주휴게소를 지나쳤어요. 목적지로 안내할게요", voice.spoken.last())
        assertEquals("여주휴게소를 지나쳤어요", crew.drops.value.single().title)
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

    // The trip's start (Crew.startedAtMillis): the host's 출발, or the end of the 3 minutes for joining.

    private fun runWithHost(
        myCar: String,
        autoStartsGuidance: Boolean = true,
        /** When joining ends, from the test's clock (NOW); null = no limit. */
        joinDeadline: Long? = null,
        /** False: 이모 차 hasn't joined yet (아빠 and 삼촌 have). */
        everyoneIn: Boolean = false,
        block: suspend (TestCrew, TestNavigation, TestVoice) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val crew = TestCrew(notStarted = true).apply { carId = myCar } // the crew's host is 아빠 차
        crew.crew.value = crew.crew.value!!.copy(
            joinDeadlineMillis = joinDeadline,
            cars = listOf(DemoData.me, DemoData.uncle, DemoData.aunt.copy(connected = everyoneIn)),
        )
        val nav = TestNavigation()
        val voice = TestVoice()
        TripCoordinator(scope, crew, TestRoute(), nav, voice, now = { NOW }, autoStartsGuidance = autoStartsGuidance).start()
        try { block(crew, nav, voice) } finally { scope.cancel() }
    }

    @Test fun aCarThatJoinedWaitsAndThenStartsWithTheHostOnce() = runWithHost(DemoData.uncle.id) { crew, nav, _ ->
        yield()
        assertEquals(0, nav.launches)
        assertEquals(DemoData.gyeongpo, nav.route.value.destination) // the route is ready while it waits
        crew.startTrip()
        yield()
        assertEquals(1, nav.launches)
        crew.drops.value = listOf(decision("later", decided = false)) // more crew news doesn't start it again
        yield()
        assertEquals(1, nav.launches)
    }

    @Test fun theHostsGuidanceStartsWithTheTripToo() = runWithHost(DemoData.me.id) { crew, nav, _ ->
        yield()
        assertEquals(0, nav.launches)
        crew.startTrip() // the host's 출발, or the end of the 3 minutes
        yield()
        assertEquals(1, nav.launches)
    }

    @Test fun anotherMapAppIsNeverOpenedWithoutATap() = runWithHost(DemoData.uncle.id, autoStartsGuidance = false) { crew, nav, _ ->
        crew.startTrip()
        yield()
        assertEquals(0, nav.launches)
    }

    @Test fun theHostsCarStartsTheTripWhenTheThreeMinutesAreOver() =
        runWithHost(DemoData.me.id, joinDeadline = NOW - 1) { crew, nav, _ ->
            yield()
            assertEquals(1, crew.tripStarts)
            assertTrue(crew.crew.value!!.isStarted)
            assertEquals(1, nav.launches)
        }

    @Test fun theTripWaitsWhileThereIsTimeLeftToJoin() =
        runWithHost(DemoData.me.id, joinDeadline = NOW + 60_000) { crew, nav, _ ->
            yield()
            assertEquals(0, crew.tripStarts)
            assertEquals(0, nav.launches)
        }

    @Test fun onlyTheHostsCarStartsTheTripByItself() =
        runWithHost(DemoData.uncle.id, joinDeadline = NOW - 1) { crew, nav, _ ->
            yield()
            assertEquals(0, crew.tripStarts) // the host's device does it; this car waits for it
            assertEquals(0, nav.launches)
        }

    @Test fun theHostsCarStartsTheTripAsSoonAsEveryCarHasJoined() =
        runWithHost(DemoData.me.id, joinDeadline = NOW + 120_000) { crew, nav, _ ->
            yield()
            assertEquals(0, crew.tripStarts) // 이모 차 is still on her way
            crew.crew.value = crew.crew.value!!.let { c -> c.copy(cars = c.cars.map { it.copy(connected = true) }) }
            yield()
            assertEquals(1, crew.tripStarts) // the last car joined: no need to wait for the 3 minutes
            assertEquals(1, nav.launches)
        }

    @Test fun everyCarInStartsTheTripOnlyFromTheHostsCar() =
        runWithHost(DemoData.uncle.id, joinDeadline = NOW + 120_000, everyoneIn = true) { crew, nav, _ ->
            yield()
            assertEquals(0, crew.tripStarts)
            assertEquals(0, nav.launches)
        }

    @Test fun aStopBeforeTheStartIsNotToldToTheCrew() = runWithHost(DemoData.uncle.id) { crew, nav, voice ->
        nav.waypointEvents.emit(WaypointEvent(DemoData.restStops[1], WaypointResult.ARRIVED))
        yield()
        assertEquals("여주휴게소에 도착했어요", voice.spoken.last()) // the driver still hears it
        assertTrue(crew.drops.value.isEmpty()) // no drops until the trip starts
    }

    private companion object {
        /** The tests' clock. */
        const val NOW = 1_000_000L
    }
}
