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
)

/**
 * DECISION drops are spoken aloud and need an answer (vote).
 * SOCIAL drops show on screen only, silently (design rule: 소셜 드랍은 화면에만).
 */
enum class DropKind { DECISION, SOCIAL }

enum class DropTopic { REST, FOOD, FUEL, DESTINATION, MESSAGE }

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
) {
    val isDecided: Boolean get() = decidedCandidateId != null
    val decidedCandidate: Candidate? get() = candidates.firstOrNull { it.id == decidedCandidateId }
}

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }

/** Everything CarDrop reads from this car. Null = the car did not report a value. */
data class VehicleSnapshot(
    val fuelPercent: Int? = null,
    val rangeKm: Float? = null,
    val gear: Gear = Gear.UNKNOWN,
    val drivingMinutes: Int? = null,
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
