package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Crew
import com.cardrop.core.model.DriverIntent
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import com.cardrop.core.model.VehicleSnapshot
import kotlinx.coroutines.delay

/**
 * Owner: lead.
 *
 * Every judgment CarDrop makes. [ClaudeAiService] is the real version (Claude API, key in
 * local.properties `cardrop.claudeApiKey`): Haiku 5.5 for readIntent and polishMessage (fast),
 * Sonnet 5.5 for recommend and localQuiz. [FakeAiService] answers when there is no key, and
 * whenever a Claude call fails ([FallbackAiService]).
 */
interface AiService {
    /** "배고파" -> FOOD, "화장실 급해" -> REST, "기름 없어" -> FUEL. */
    suspend fun readIntent(utterance: String): DriverIntent

    /** Picks 2-3 candidates from [TripContext.pool] for the whole crew, ranked best first. */
    suspend fun recommend(topic: DropTopic, context: TripContext): List<Candidate>

    /** "삼촌 차에 곧 도착한다고 전해줘" -> "곧 도착해요!" */
    suspend fun polishMessage(raw: String): String

    /** A question about the region the crew is driving through (board 15). */
    suspend fun localQuiz(regionName: String): QuizQuestion
}

/** What the AI sees when it recommends a stop (board 10: 경로 · 도착 시간 · 연료 · 연속 운전). */
data class TripContext(
    val crew: Crew,
    val positions: Map<String, CarPosition>,
    val myVehicle: VehicleSnapshot,
    /** Places the AI may choose from. It must not invent places. */
    val pool: List<Place> = DemoData.restStops,
    /**
     * True when the caller already kept only the places that fit (board 05: stations the car can reach, maybe in
     * town and not on the route): the AI picks among all of them instead of narrowing them to stops 25 km ahead.
     */
    val poolIsFinal: Boolean = false,
)

/**
 * Destinations and cafés (by the destination beach) sit at the end of the trip, so they aren't
 * filtered down to the stops every car still has ahead on the highway.
 */
internal fun DropTopic.isNearDestination(): Boolean = this == DropTopic.DESTINATION || this == DropTopic.CAFE

/**
 * Keyword rules and canned answers, with a short delay so loading states are visible.
 * [stopsAhead] filters the pool to places every car still has ahead (AppContainer.stopsAhead).
 */
class FakeAiService(
    private val stopsAhead: (List<Place>) -> List<Place> = { it },
) : AiService {

    override suspend fun readIntent(utterance: String): DriverIntent {
        delay(500)
        val text = utterance.replace(" ", "")
        val topic = when {
            listOf("배고", "밥", "식사", "먹").any { it in text } -> DropTopic.FOOD
            listOf("기름", "주유", "연료").any { it in text } -> DropTopic.FUEL
            listOf("화장실", "쉬", "휴게소", "졸려").any { it in text } -> DropTopic.REST
            listOf("커피", "카페").any { it in text } -> DropTopic.CAFE
            listOf("목적지", "바꾸").any { it in text } -> DropTopic.DESTINATION
            else -> DropTopic.MESSAGE
        }
        return DriverIntent(topic, confidence = 0.6f, message = utterance.takeIf { topic == DropTopic.MESSAGE })
    }

    override suspend fun recommend(topic: DropTopic, context: TripContext): List<Candidate> {
        delay(900)
        val reasons = when (topic) {
            DropTopic.FOOD -> listOf("4대 모두 20분 안에 도착", "평점 4.6 · 대기 짧음", "한우 맛집")
            DropTopic.FUEL -> listOf("남은 연료로 갈 수 있어요", "가장 저렴한 휘발유", "충전소도 있어요")
            DropTopic.CAFE -> listOf("목적지에서 1.5km", "강문해변 바로 앞", "안목 커피거리에 있어요")
            DropTopic.DROWSY -> listOf("가장 가까운 졸음쉼터", "화장실 있어요", "4대 모두 지나가는 길")
            DropTopic.STORE -> listOf("가장 가까운 편의점", "주차하기 편해요", "24시간 열어요")
            DropTopic.EV -> listOf("가장 가까운 충전소", "급속 충전기", "대기 짧음")
            else -> listOf("4대 모두 지나가는 길", "도착 시간 차이 5분", "넓은 주차장")
        }
        // Stops on the way: only those every car still has ahead (a 1-minute vote must end before
        // anyone passes them). Destinations and cafés are near the end of the trip: all of them.
        val ahead = if (topic.isNearDestination() || context.poolIsFinal) {
            context.pool
        } else {
            stopsAhead(context.pool).ifEmpty { context.pool.takeLast(3) }
        }
        return ahead.take(3).mapIndexed { i, place ->
            Candidate(id = place.id, place = place, reason = reasons[i], detourMinutes = 2 + i * 3)
        }
    }

    override suspend fun polishMessage(raw: String): String {
        delay(400)
        return raw.trim().removeSuffix("전해줘").removeSuffix("라고").trim().ifEmpty { raw }
    }

    override suspend fun localQuiz(regionName: String): QuizQuestion {
        delay(700)
        return QuizQuestion(
            question = "지금 지나는 이천은 무엇으로 유명할까요?",
            tiles = listOf("이천", "?"),
            answers = listOf("쌀", "귤", "인삼", "굴비"),
            correctAnswer = "쌀",
        )
    }
}
