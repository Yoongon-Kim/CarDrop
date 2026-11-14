package com.cardrop.crew

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.drop.VoteRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bot crew's trip start (the 2 minutes for joining, the bots left out at 출발, no drops before it) and the bots'
 * votes.
 */
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

    // ---- The bots' votes ----

    private val bots = listOf(DemoData.uncle, DemoData.minji, DemoData.aunt)
    private val cars = listOf(DemoData.me.id) + bots.map { it.id }

    private fun places(vararg names: String) =
        names.map { Candidate(it, Place(it, it, 37.0, 128.0), reason = "", detourMinutes = 0) }

    /** This car's vote drop after every bot answered and this car voted for [mine]. */
    private fun vote(places: List<Candidate>, mine: Candidate): Drop = Drop(
        id = "vote", fromCarId = DemoData.me.id, kind = DropKind.DECISION, topic = DropTopic.CAFE, title = "",
        candidates = places, createdAtMillis = 0, deadlineMillis = 120_000,
        votes = bots.associate { it.id to FakeCrewRepository.botChoice(it, places)!!.id } + (DemoData.me.id to mine.id),
    )

    @Test fun everyBotAnswersSoAVoteEndsOnceThisCarAnswers() {
        val three = places("강문 스타벅스", "강문 덴켄", "안목 보사노바")
        assertEquals(three, bots.map { FakeCrewRepository.botChoice(it, three) }) // one place each, 이모 too
        for (mine in three) {
            val drop = vote(three, mine)
            assertTrue(VoteRules.isOver(drop, cars, nowMillis = 10_000)) // long before the 2 minutes
            assertEquals(mine.id, VoteRules.winner(drop, cars)) // this car's answer decides
        }
    }

    @Test fun withTwoPlacesThisCarsAnswerStillDecides() {
        val two = places("덕평자연휴게소", "여주휴게소")
        for (mine in two) assertEquals(mine.id, VoteRules.winner(vote(two, mine), cars))
    }

    @Test fun everyBotSaysYesToAProposal() {
        val one = places("속초 해수욕장")
        assertEquals(List(3) { one.single() }, bots.map { FakeCrewRepository.botChoice(it, one) })
    }
}
