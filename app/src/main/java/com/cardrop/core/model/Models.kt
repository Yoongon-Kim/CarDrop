package com.cardrop.core.model

/*
 * Shared data types used by every area. Owned by the lead: ask before changing a type here,
 * because all three areas compile against it.
 */

/** A point someone can drive to: a destination, a rest stop, a gas station. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val detail: String = "",
)

/** Colours from the design (blue = me, purple = 삼촌, yellow = 민지, teal = 이모). */
enum class CarColor { BLUE, PURPLE, TEAL, YELLOW, GRAY }

data class Car(
    val id: String,
    val name: String,
    val color: CarColor,
    val isMe: Boolean = false,
    val isBot: Boolean = false,
    val connected: Boolean = true,
)

data class Crew(
    /** 6-digit join code, e.g. "482917". */
    val code: String,
    val name: String,
    val destination: Place,
    val hostCarId: String,
    val cars: List<Car>,
    /**
     * Cars can join until then: 1 minute after the host made the crew (`JoinRules.JOIN_WINDOW_MILLIS`). At that
     * time the trip starts by itself. Null = no time limit.
     */
    val joinDeadlineMillis: Long? = null,
    /**
     * When the trip started: the host's 출발 (board 01), the moment every car had joined, or the join deadline.
     * Null = not yet, and until then no car sends drops. The cars that hadn't joined by then are left out: they are
     * no longer in [cars].
     */
    val startedAtMillis: Long? = null,
) {
    val isStarted: Boolean get() = startedAtMillis != null
}

/**
 * Who a drop goes to, as the screens say it (design board 08): "크루 전체", or the chosen cars'
 * names in crew order, e.g. "삼촌 차 · 민지 차". Choosing every other car counts as the whole crew.
 */
fun Crew.recipientLabel(toCarIds: List<String>, myCarId: String): String {
    val others = cars.filterNot { it.id == myCarId }
    val chosen = others.filter { it.id in toCarIds }
    return if (chosen.isEmpty() || chosen.size == others.size) "크루 전체" else chosen.joinToString(" · ") { it.name }
}

/**
 * DECISION drops are spoken aloud and need an answer (vote).
 * SOCIAL drops show on screen only, silently (design rule: 소셜 드랍은 화면에만).
 */
enum class DropKind { DECISION, SOCIAL }

enum class DropTopic {
    REST,
    FOOD,
    FUEL,
    DESTINATION,
    MESSAGE,

    /** 카페: the AI picks from cafés near the destination (DemoData.cafes). */
    CAFE,

    /** 직접 추가: no AI picks; the driver adds the places for the vote. */
    CUSTOM,

    /** 노래 드랍 (boards 12, 13): a SOCIAL drop with a song (drop/SongDrop.kt), never voted on. Member A. */
    SONG,

    /**
     * 게임 초대 (Member A, 2026-11-15): a SOCIAL drop that goes with an omok 도전장 (the game's id in `message`) or a
     * quiz 도전장. Never spoken: a car in P gets a pop-up (수락 / 거절), a car driving finds it only in its inbox.
     */
    GAME,

    /**
     * 음성 메시지 (board 09's 녹음해서 보내기, Member A, 2026-11-15): a SOCIAL drop with a voice recording of at most 20 s,
     * Base64 in `message` (drop/VoiceClip.kt). Never spoken by the app: the receiver plays it with ▶.
     */
    VOICE,

    /** 졸음쉼터 (Member A, 2026-11-15): board 08's own tiles (drop/DropSlots.kt); the AI picks from 영동고속도로's 졸음쉼터. */
    DROWSY,

    /** 편의점 (Member A, 2026-11-15): board 08's own tiles; the AI picks from Kakao's 편의점 near the car. */
    STORE,

    /** 전기차 충전 (Member A, 2026-11-15): board 08's own tiles; the AI picks from Kakao's chargers near the car. */
    EV,
}

data class Candidate(
    val id: String,
    val place: Place,
    /** Short reason shown under the name, written by the AI. */
    val reason: String,
    val detourMinutes: Int,
)

data class Drop(
    val id: String,
    val fromCarId: String,
    val kind: DropKind,
    val topic: DropTopic,
    val title: String,
    val message: String = "",
    val candidates: List<Candidate> = emptyList(),
    /** carId -> candidateId */
    val votes: Map<String, String> = emptyMap(),
    val createdAtMillis: Long,
    /** After this time, cars that did not answer count as voting for candidate 1. */
    val deadlineMillis: Long,
    /** Set once the vote is over. */
    val decidedCandidateId: String? = null,
    /** The cars it was sent to besides the sender (board 08 "받는 차"). Empty = the whole crew. */
    val toCarIds: List<String> = emptyList(),
    /**
     * False for a quick drop's vote (board 04's 휴게소 / 식사 / 주유 pop-up): the car that sent it doesn't vote, so the
     * vote ends as soon as the other cars have answered, and only their answers decide (lead, 2026-11-15). The decided
     * stop still goes into the sender's route. True for every other drop: its sender votes like the cars it went to.
     */
    val senderVotes: Boolean = true,
) {
    val isDecided: Boolean get() = decidedCandidateId != null
    val decidedCandidate: Candidate? get() = candidates.firstOrNull { it.id == decidedCandidateId }

    /** True when [carId] takes part: the sender, a receiver, or any car when it went to the whole crew. */
    fun isFor(carId: String): Boolean = toCarIds.isEmpty() || carId == fromCarId || carId in toCarIds

    /** True when [carId]'s answer counts in a vote: a car that takes part ([isFor]), except a quick drop's sender. */
    fun isVoter(carId: String): Boolean = isFor(carId) && (senderVotes || carId != fromCarId)

    /** The cars whose answers count in a vote ([isVoter]), in crew order. */
    fun voterIds(crewCarIds: List<String>): List<String> = crewCarIds.filter(::isVoter)
}

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/** Everything CarDrop reads from this car. Null = the car did not report a value. */
data class VehicleSnapshot(
    val fuelPercent: Int? = null,
    val rangeKm: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
    val drivingMinutes: Int? = null,
    /** Monotonic time of a successful gear read; game readiness expires if the SDK stops answering. */
    val gearCheckedAtElapsedMillis: Long? = null,
) {
    val isParked: Boolean get() = gear == Gear.PARK
}

/** One car's position on the shared route, from the RouteSimulator. */
data class CarPosition(
    val carId: String,
    val lat: Double,
    val lng: Double,
    val kmToDestination: Float,
    val etaMinutes: Int,
)

/** What we last asked the navigation app to do. */
data class NavRoute(
    val destination: Place? = null,
    val waypoint: Place? = null,
    /** Short message for the UI, e.g. "경유지를 덕평자연휴게소로 바꿨어요". */
    val lastAction: String? = null,
)

/** Result of the AI reading what a driver said ("배고파" -> FOOD). */
data class DriverIntent(
    val topic: DropTopic,
    val confidence: Float,
    /** Text for a MESSAGE drop, when the topic is MESSAGE. */
    val message: String? = null,
)

data class QuizQuestion(
    val question: String,
    val tiles: List<String>,
    val answers: List<String>,
    val correctAnswer: String,
)
