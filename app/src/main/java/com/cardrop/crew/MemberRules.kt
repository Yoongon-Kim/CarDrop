package com.cardrop.crew

import com.cardrop.core.model.Car
import com.cardrop.core.model.Crew

/**
 * Owner: Member A (board 01's design work: lead). Plain rules for board 01's member rows, kept out of the
 * screen so they can be unit tested (MemberRulesTest).
 */
object MemberRules {
    /**
     * The grey line under a car's name (design line 638): "방장 · 연료 64%", "코드로 참여 · 방금 전",
     * "초대 보냄 · 응답 기다리는 중". The fuel shows only on the host's row, and only when the host is this car:
     * the other cars' fuel isn't shared. [joinedAgo] is null when this screen didn't see the car connect.
     */
    fun meta(car: Car, hostCarId: String, myCarId: String, myFuelPercent: Int?, joinedAgo: String?): String = when {
        car.id == hostCarId -> listOfNotNull("방장", myFuelPercent?.takeIf { car.id == myCarId }?.let { "연료 $it%" })
            .joinToString(" · ")
        car.connected -> listOfNotNull("코드로 참여", joinedAgo).joinToString(" · ")
        else -> "초대 보냄 · 응답 기다리는 중"
    }

    /** "방금 전" under a minute, then "3분 전", then "2시간 전". */
    fun agoText(elapsedMillis: Long): String = when {
        elapsedMillis < MINUTE -> "방금 전"
        elapsedMillis < HOUR -> "${elapsedMillis / MINUTE}분 전"
        else -> "${elapsedMillis / HOUR}시간 전"
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}

/**
 * When each car of the crew connected, as far as this screen saw it (board 01's "코드로 참여 · 방금 전").
 * The cars already in when the screen first looks get no time: it doesn't know when they came. A car that
 * leaves loses its time. Immutable: [next] gives the updated one, so the screen can keep it in a State.
 */
data class JoinTimes(
    val crewCode: String? = null,
    val connected: Set<String> = emptySet(),
    /** Car id -> when this screen saw it connect (System.currentTimeMillis()). */
    val since: Map<String, Long> = emptyMap(),
) {
    fun next(crew: Crew, nowMillis: Long): JoinTimes {
        val now = crew.cars.filter { it.connected }.map { it.id }.toSet()
        if (crew.code != crewCode) return JoinTimes(crew.code, now) // a new crew: start looking
        val added = (now - connected).associateWith { nowMillis }
        return copy(connected = now, since = (since + added).filterKeys { it in now })
    }

    /** "방금 전", "3분 전", or null when this screen didn't see [carId] connect. */
    fun agoText(carId: String, nowMillis: Long): String? = since[carId]?.let { MemberRules.agoText(nowMillis - it) }
}
