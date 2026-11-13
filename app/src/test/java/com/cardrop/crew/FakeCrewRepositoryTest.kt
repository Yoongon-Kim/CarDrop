package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bot crew's trip start: the 2 minutes for joining, and the bots left out at 출발. */
class FakeCrewRepositoryTest {
    private var clock = 1_000_000L

    private fun withCrew(block: suspend (FakeCrewRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { block(FakeCrewRepository(scope, now = { clock })) } finally { scope.cancel() }
    }

    @Test fun aNewCrewGivesTheOtherCarsTwoMinutesToJoin() = withCrew { repo ->
        val crew = repo.createCrew("강릉 여행", DemoData.gyeongpo)
        assertEquals(clock + JoinRules.JOIN_WINDOW_MILLIS, crew.joinDeadlineMillis)
        assertFalse(crew.isStarted)
    }

    @Test fun theBotsThatHaventJoinedAtTheStartAreLeftOut() = withCrew { repo ->
        repo.createCrew("강릉 여행", DemoData.gyeongpo) // the bots join a few seconds later
        clock += 5_000
        repo.startTrip()
        val crew = repo.crew.value!!
        assertEquals(clock, crew.startedAtMillis)
        assertEquals(listOf(DemoData.me.id), crew.cars.map { it.id })
    }

    @Test fun startingAgainKeepsTheFirstStart() = withCrew { repo ->
        repo.createCrew("강릉 여행", DemoData.gyeongpo)
        repo.startTrip()
        val first = repo.crew.value!!.startedAtMillis
        clock += 60_000
        repo.startTrip()
        assertEquals(first, repo.crew.value!!.startedAtMillis)
    }

    @Test fun aCrewJoinedWithItsCodeIsOnTheRoadAlready() = withCrew { repo ->
        val crew = repo.joinCrew(DemoData.DEMO_CREW_CODE)!!
        assertTrue(crew.isStarted)
        assertEquals(4, crew.cars.size)
    }
}
