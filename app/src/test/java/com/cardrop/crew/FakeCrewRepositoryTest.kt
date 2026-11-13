package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bot crew's trip start: the 2 minutes for joining, the bots left out at 출발, and no drops before it. */
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

    @Test fun noDropGoesOutBeforeTheTripStarts() = withCrew { repo ->
        val draft = DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = "곧 출발해요")
        assertTrue(runCatching { repo.sendDrop(draft) }.isFailure) // no crew yet
        repo.createCrew("강릉 여행", DemoData.gyeongpo)
        assertTrue(runCatching { repo.sendDrop(draft) }.exceptionOrNull() is TripNotStartedException)
        assertTrue(repo.drops.value.isEmpty())

        repo.startTrip()
        repo.sendDrop(draft)
        assertEquals(listOf("곧 출발해요"), repo.drops.value.map { it.title })
    }

    @Test fun theBotsSendNothingBeforeTheTripStarts() = withCrew { repo ->
        repo.createCrew("강릉 여행", DemoData.gyeongpo)
        repo.injectDrop(DemoData.uncle, DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = "출발 전"))
        assertTrue(repo.drops.value.isEmpty())
    }

    @Test fun aCrewJoinedWithItsCodeIsOnTheRoadAlready() = withCrew { repo ->
        val crew = repo.joinCrew(DemoData.DEMO_CREW_CODE)!!
        assertTrue(crew.isStarted)
        assertEquals(4, crew.cars.size)
    }
}
