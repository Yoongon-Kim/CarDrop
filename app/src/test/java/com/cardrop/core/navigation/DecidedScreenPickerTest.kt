package com.cardrop.core.navigation

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.drive.TestCrew
import com.cardrop.drive.TestNavigation
import com.cardrop.drive.TestRoute
import com.cardrop.drive.TestVoice
import com.cardrop.drive.TripCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 14 must open for exactly the decision that TripCoordinator put into the route. */
class DecidedScreenPickerTest {
    private val me = DemoData.me.id
    private val stopA = DemoData.restStops[0]
    private val stopB = DemoData.restStops[1]

    /** A finished vote won by [place]. [joined] = this car voted for it (board 06 "같이 갈래요"). */
    private fun decided(
        id: String,
        at: Long,
        place: Place = stopA,
        topic: DropTopic = DropTopic.REST,
        from: String = DemoData.uncle.id,
        joined: Boolean = false,
        toCarIds: List<String> = emptyList(),
    ): Drop {
        val winner = Candidate(place.id, place, "test", 2)
        return Drop(
            id = id, fromCarId = from, kind = DropKind.DECISION, topic = topic, title = "test",
            candidates = listOf(winner), votes = if (joined) mapOf(me to winner.id) else emptyMap(),
            createdAtMillis = at, deadlineMillis = 0, decidedCandidateId = winner.id, toCarIds = toCarIds,
        )
    }

    /** The real TripCoordinator and the picker watch the same drops, like in the app. */
    private class Trip(val crew: TestCrew, val nav: TestNavigation, val voice: TestVoice) {
        private val picker = DecidedScreenPicker(crew.myCarId, nav.decisions::isSkipped)

        /** The crew's drops become [drops]. Returns the drop whose board 14 opens, or null. */
        suspend fun update(vararg drops: Drop): Drop? {
            crew.drops.value = drops.toList()
            yield() // TripCoordinator updates the route
            return picker.pick(crew.crew.value, drops.toList())
        }
    }

    private fun trip(block: suspend Trip.() -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val trip = Trip(TestCrew(), TestNavigation(), TestVoice())
            TripCoordinator(scope, trip.crew, TestRoute(), trip.nav, trip.voice).start()
            trip.block()
        } finally {
            scope.cancel()
        }
    }

    @Test fun anOlderVoteThatEndsAfterANewerOneDoesNotOpenBoard14() = trip {
        val newer = decided("newer", at = 10, place = stopB)
        assertEquals(newer, update(newer))
        assertEquals(stopB, nav.route.value.waypoint)

        // The route keeps the newer stop, so board 14 must not say the older one was chosen.
        val older = decided("older", at = 5, place = stopA)
        assertNull(update(newer, older))
        assertEquals(stopB, nav.route.value.waypoint)
        assertEquals(1, voice.spoken.size)
    }

    @Test fun decisionsArrivingTogetherOpenOnlyTheNewest() = trip {
        val old = decided("old", at = 1, place = stopA)
        val new = decided("new", at = 10, place = stopB)
        assertEquals(new, update(old, new))
        assertEquals(stopB, nav.route.value.waypoint)
        assertNull(update(old, new)) // the same list again opens nothing
    }

    @Test fun aFuelStopOpensOnlyForTheCarThatSentOrJoinedIt() = trip {
        val notJoined = decided("not-joined", at = 1, topic = DropTopic.FUEL)
        assertNull(update(notJoined))
        assertNull(nav.route.value.waypoint)

        val joined = decided("joined", at = 2, place = stopB, topic = DropTopic.FUEL, joined = true)
        assertEquals(joined, update(notJoined, joined))
        assertEquals(stopB, nav.route.value.waypoint)

        val own = decided("own", at = 3, place = stopA, topic = DropTopic.FUEL, from = me)
        assertEquals(own, update(notJoined, joined, own))
        assertEquals(stopA, nav.route.value.waypoint)
    }

    @Test fun aStopThisCarTurnedDownDoesNotOpen() = trip {
        nav.decisions.skip("declined") // board 06 "가던 길 갈래요"
        assertNull(update(decided("declined", at = 1, topic = DropTopic.FUEL, joined = true)))
        assertNull(nav.route.value.waypoint)
    }

    @Test fun aVoteSentOnlyToOtherCarsNeverOpens() = trip {
        assertNull(update(decided("private", at = 1, toCarIds = listOf(DemoData.aunt.id))))
        assertNull(nav.route.value.waypoint)
    }

    @Test fun aNewCrewStartsOver() {
        fun crew(code: String) = Crew(code, "test", DemoData.gyeongpo, me, listOf(DemoData.me, DemoData.uncle))
        val picker = DecidedScreenPicker(me)
        val first = decided("first", at = 10)
        assertEquals(first, picker.pick(crew("111111"), listOf(first)))
        // The new crew's votes can be older than the old crew's last decision.
        val next = decided("next", at = 5)
        assertEquals(next, picker.pick(crew("222222"), listOf(next)))
    }
}
