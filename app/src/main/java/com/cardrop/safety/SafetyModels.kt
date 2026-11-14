package com.cardrop.safety

import com.cardrop.core.model.Car
import kotlin.math.max

/** Versioned prototype rules; these are app scoring thresholds, not enforcement thresholds. */
object SafetyRules {
    const val VERSION = 2
    const val MAX_GAP_SECONDS = 0.35
    const val ACCELERATION = 3.0
    const val BRAKING = -3.5
    const val MOTION_HOLD = 0.5
}

enum class SafetyEventType(val label: String) { ACCELERATION("급가속"), BRAKING("급정거") }
enum class SafetyTripStatus { RECORDING, COMPLETED, STOPPED }

data class SafetyEvent(
    val id: String,
    val type: SafetyEventType,
    val atMillis: Long,
    val penalty: Int,
    val peak: Double = 0.0,
)

data class SafetyRecord(
    val tripId: String,
    val carId: String,
    val carName: String,
    val destination: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val demo: Boolean,
    val status: SafetyTripStatus = SafetyTripStatus.RECORDING,
    val endedAtMillis: Long? = null,
    val rulesVersion: Int = SafetyRules.VERSION,
    val events: List<SafetyEvent> = emptyList(),
    val eligibleSeconds: Double = 0.0,
    val observedSeconds: Double = 0.0,
    val movingSeconds: Double = 0.0,
    val distanceKm: Double = 0.0,
    val accelerationLocked: Boolean = false,
    val brakingLocked: Boolean = false,
    val revision: Long = 0,
) {
    val id: String get() = "$tripId|$carId|${if (demo) "demo" else "vehicle"}"
    val totalPenalty: Int get() = events.sumOf { it.penalty }
    val score: Int get() = max(0, 100 - totalPenalty)
    val speedCoverage: Double get() = if (eligibleSeconds > 0) (observedSeconds / eligibleSeconds).coerceIn(0.0, 1.0) else 0.0
    val rankEligible: Boolean get() = status != SafetyTripStatus.STOPPED && movingSeconds >= 60 && speedCoverage >= 0.95 && rulesVersion == SafetyRules.VERSION
    fun count(type: SafetyEventType): Int = events.count { it.type == type }
    fun penalty(type: SafetyEventType): Int = events.filter { it.type == type }.sumOf { it.penalty }
}

data class SpeedReading(val metresPerSecond: Double, val measuredNanos: Long, val receivedNanos: Long)

data class SafetyPeer(
    val carId: String,
    val score: Int,
    val accelerationCount: Int,
    val brakingCount: Int,
    val totalPenalty: Int,
    val speedCoverage: Double,
    val movingSeconds: Double,
    val status: SafetyTripStatus,
    val demo: Boolean,
    val updatedAtMillis: Long,
    val rulesVersion: Int = SafetyRules.VERSION,
) {
    fun comparable(now: Long, mode: Boolean): Boolean = demo == mode && rulesVersion == SafetyRules.VERSION &&
        status != SafetyTripStatus.STOPPED && movingSeconds >= 60 && speedCoverage >= .95 &&
        (status != SafetyTripStatus.RECORDING || now - updatedAtMillis in 0..15_000)
}

fun SafetyRecord.peer() = SafetyPeer(carId, score, count(SafetyEventType.ACCELERATION), count(SafetyEventType.BRAKING),
    totalPenalty, speedCoverage, movingSeconds,
    status, demo, updatedAtMillis, rulesVersion)

data class RankedSafetyCar(val car: Car, val peer: SafetyPeer?, val rank: Int?, val provisional: Boolean = false, val basis: String? = null)

/** Live ranks are provisional within the same measured categories; final eligibility remains strict. */
fun rankSafetyCars(cars: List<Car>, peers: Map<String, SafetyPeer>, now: Long, demo: Boolean, live: Boolean = false): List<RankedSafetyCar> {
    fun basis(p: SafetyPeer): String? {
        if (!live) return if (p.comparable(now, demo)) "급가감속" else null
        if (p.demo != demo || p.rulesVersion != SafetyRules.VERSION || p.status == SafetyTripStatus.STOPPED ||
            p.movingSeconds < 1 || p.speedCoverage <= 0 ||
            (p.status == SafetyTripStatus.RECORDING && now - p.updatedAtMillis !in 0..15_000)) return null
        return "급가감속"
    }
    val eligible = cars.mapNotNull { car -> peers[car.id]?.let { p -> basis(p)?.let { Triple(car.id, p.score, it) } } }
    return cars.map { car ->
        val peer = peers[car.id]
        val group = peer?.let(::basis)
        val rank = if (group != null) 1 + eligible.count { it.third == group && it.second > peer.score } else null
        RankedSafetyCar(car, peer, rank, provisional = live && rank != null, basis = group)
    }.sortedWith(compareBy<RankedSafetyCar> { if (it.rank == null) 1 else 0 }.thenBy { it.rank ?: Int.MAX_VALUE }.thenBy { it.car.id })
}
