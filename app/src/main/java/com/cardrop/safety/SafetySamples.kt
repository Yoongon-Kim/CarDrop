package com.cardrop.safety

import java.util.Calendar

/**
 * Sample records for the 시연 (Member A, 2026-11-15): with the DEMO (fake) car, board 16's "이번 주 내 기록" would say
 * "완료된 주행 기록이 아직 없어요". So the demo shows three finished trips of this week, each one openable on board 17.
 * They are only in memory (never saved or sent to the crew), and only in demo mode ([SafetyCoordinator.demo]).
 * Plain Kotlin, so SafetySamplesTest covers them.
 */
object SafetySamples {
    /** id prefix of the sample trips, so they never mix with a real trip's id. */
    const val TRIP_PREFIX = "sample-week-"

    private class Trip(val destination: String, val minutes: Int, val km: Double, val accelerations: Int, val brakings: Int)

    /** 96, 91 and 100 points: 2 x 급가속; 3 x 급가속 + 1 x 급정거; none. Average 96. */
    private val trips = listOf(
        Trip("속초 해수욕장", 74, 112.4, accelerations = 2, brakings = 0),
        Trip("양양 서피비치", 68, 98.7, accelerations = 3, brakings = 1),
        Trip("춘천 남이섬", 52, 71.3, accelerations = 0, brakings = 0),
    )

    /**
     * This week's sample trips for [carId], oldest first: spread between Monday 00:00 and [now] (at 15%, 45% and
     * 75% of that time), so they always count as this week. Each is finished (COMPLETED) and meets the rank rules.
     */
    fun week(carId: String, carName: String, now: Long): List<SafetyRecord> {
        val weekStart = startOfWeek(now)
        val span = (now - weekStart).coerceAtLeast(0)
        return trips.mapIndexed { i, trip ->
            val start = weekStart + (span * listOf(0.15, 0.45, 0.75)[i]).toLong()
            val seconds = trip.minutes * 60.0
            val end = start + trip.minutes * 60_000L
            // The marks spread over the trip, a little after the start.
            val types = List(trip.accelerations) { SafetyEventType.ACCELERATION } + List(trip.brakings) { SafetyEventType.BRAKING }
            val events = types.mapIndexed { n, type ->
                SafetyEvent(
                    id = "$TRIP_PREFIX$i-$n",
                    type = type,
                    atMillis = start + (end - start) * (n + 1) / (types.size + 1),
                    penalty = if (type == SafetyEventType.ACCELERATION) 2 else 3,
                    peak = if (type == SafetyEventType.ACCELERATION) 3.4 else -4.1,
                )
            }
            SafetyRecord(
                tripId = "$TRIP_PREFIX${i + 1}",
                carId = carId,
                carName = carName,
                destination = trip.destination,
                startedAtMillis = start,
                updatedAtMillis = end,
                demo = true,
                status = SafetyTripStatus.COMPLETED,
                endedAtMillis = end,
                events = events,
                eligibleSeconds = seconds,
                observedSeconds = seconds,
                movingSeconds = seconds,
                distanceKm = trip.km,
            )
        }
    }

    /** [stored] with this week's samples added (newest first), unless a record with the same id is already there. */
    fun withSamples(stored: List<SafetyRecord>, carId: String, carName: String, now: Long): List<SafetyRecord> {
        val samples = week(carId, carName, now).filter { s -> stored.none { it.id == s.id } }
        return (stored + samples).sortedByDescending { it.updatedAtMillis }
    }
}

/** Monday 00:00 of [now]'s week, on this device's clock (board 16's "이번 주"). */
internal fun startOfWeek(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now; firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
