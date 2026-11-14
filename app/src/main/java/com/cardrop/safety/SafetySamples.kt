package com.cardrop.safety

import com.cardrop.core.demo.DemoData
import java.util.Calendar

/**
 * Sample records for the 시연 (Member A, 2026-11-15): with the DEMO (fake) car, board 16's "이번 주 내 기록" would say
 * "완료된 주행 기록이 아직 없어요", and a demo drive scores 100 unless the DEMO panel's 급가속 / 급정거 is pressed. So the
 * demo shows three finished trips of this week ([week]) and three of the weeks before ([past], only in 지난 내 차 기록),
 * with varied scores, each one openable on board 17.
 * They are only in memory (never saved or sent to the crew), and only in demo mode ([SafetyCoordinator.demo]).
 * Plain Kotlin, so SafetySamplesTest covers them.
 */
object SafetySamples {
    /** id prefix of the sample trips, so they never mix with a real trip's id. */
    const val TRIP_PREFIX = "sample-week-"

    private class Trip(val destination: String, val minutes: Int, val km: Double, val accelerations: Int, val brakings: Int)

    /** This week: 96, 91 and 100 points (2 x 급가속; 3 x 급가속 + 1 x 급정거; none). Average 96. */
    private val trips = listOf(
        Trip("속초 해수욕장", 74, 112.4, accelerations = 2, brakings = 0),
        Trip("양양 서피비치", 68, 98.7, accelerations = 3, brakings = 1),
        Trip("춘천 남이섬", 52, 71.3, accelerations = 0, brakings = 0),
    )

    /** The weeks before, with how many days before this Monday: 88, 94 and 85 points. */
    private val pastTrips = listOf(
        2 to Trip("강릉 안목해변", 81, 121.6, accelerations = 3, brakings = 2),
        5 to Trip("평창 대관령 양떼목장", 63, 89.2, accelerations = 3, brakings = 0),
        10 to Trip("가평 쁘띠프랑스", 57, 68.4, accelerations = 3, brakings = 3),
    )

    /**
     * This week's sample trips for [carId], oldest first: spread between Monday 00:00 and [now] (at 15%, 45% and
     * 75% of that time), so they always count as this week. Each is finished (COMPLETED) and meets the rank rules.
     */
    fun week(carId: String, carName: String, now: Long): List<SafetyRecord> {
        val weekStart = startOfWeek(now)
        val span = (now - weekStart).coerceAtLeast(0)
        return trips.mapIndexed { i, trip -> record(i + 1, trip, weekStart + (span * listOf(0.15, 0.45, 0.75)[i]).toLong(), carId, carName) }
    }

    /** Three finished sample trips before this week (2, 5 and 10 days before Monday, mid-afternoon), newest first. */
    fun past(carId: String, carName: String, now: Long): List<SafetyRecord> {
        val weekStart = startOfWeek(now)
        return pastTrips.mapIndexed { i, (days, trip) ->
            record(trips.size + i + 1, trip, weekStart - days * 86_400_000L + 14 * 3_600_000L, carId, carName)
        }
    }

    /** Sample trip number [n] ([TRIP_PREFIX] + n), started at [start], with its 급가속 / 급정거 spread over the trip. */
    private fun record(n: Int, trip: Trip, start: Long, carId: String, carName: String): SafetyRecord {
        val seconds = trip.minutes * 60.0
        val end = start + trip.minutes * 60_000L
        val types = List(trip.accelerations) { SafetyEventType.ACCELERATION } + List(trip.brakings) { SafetyEventType.BRAKING }
        val events = types.mapIndexed { k, type ->
            SafetyEvent(
                id = "$TRIP_PREFIX$n-$k",
                type = type,
                atMillis = start + (end - start) * (k + 1) / (types.size + 1),
                penalty = if (type == SafetyEventType.ACCELERATION) 2 else 3,
                peak = if (type == SafetyEventType.ACCELERATION) 3.4 else -4.1,
            )
        }
        return SafetyRecord(
            tripId = "$TRIP_PREFIX$n",
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

    /**
     * The name the sample trips carry (board 17 shows it): this device's car among the four demo cars, by [carId], as the
     * crew names this car ("삼촌 차 (나)", like a real record). The samples are made as the app starts, before this car's
     * crew exists, so the crew can't name it: they used to say "아빠 차" on every device, also on 삼촌 · 민지 · 이모 차's
     * (lead, 2026-11-15).
     */
    fun carName(carId: String): String {
        val car = listOf(DemoData.me, DemoData.uncle, DemoData.minji, DemoData.aunt).firstOrNull { it.id == carId } ?: return "내 차"
        return if ("(나)" in car.name) car.name else "${car.name} (나)"
    }

    /** [stored] with all the samples added (newest first), unless a record with the same id is already there. */
    fun withSamples(stored: List<SafetyRecord>, carId: String, carName: String, now: Long): List<SafetyRecord> {
        val samples = (week(carId, carName, now) + past(carId, carName, now)).filter { s -> stored.none { it.id == s.id } }
        return (stored + samples).sortedByDescending { it.updatedAtMillis }
    }
}

/** Monday 00:00 of [now]'s week, on this device's clock (board 16's "이번 주"). */
internal fun startOfWeek(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now; firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
