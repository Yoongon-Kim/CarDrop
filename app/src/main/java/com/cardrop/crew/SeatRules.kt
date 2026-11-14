package com.cardrop.crew

/**
 * Owner: Member A (2026-11-15). Which seat a car gets when it joins a real crew with the code (board 03). The app still
 * has 4 seats (아빠 / 삼촌 / 민지 / 이모 차: colours, badges, the GPS order); the host has the first, and a joining car
 * takes the first free one, shown under the name its driver typed. Pure Kotlin, unit tested (SeatRulesTest).
 */
object SeatRules {
    /** One seat as the crew has it: who took it ([device], null = nobody yet), and whether it was left out at the start. */
    data class Seat(val id: String, val device: String?, val connected: Boolean, val out: Boolean)

    sealed interface Pick {
        data class Take(val id: String) : Pick

        /** Every seat is taken. */
        data object Full : Pick

        /** The trip started without this device. */
        data object Closed : Pick
    }

    /**
     * The seat for [deviceId] among [seats] (not the host's), in order: its own seat again (it joined this crew
     * before, e.g. before an app restart), unless the trip left it out; else the first seat nobody took and the trip
     * didn't close.
     */
    fun pick(seats: List<Seat>, deviceId: String): Pick {
        seats.firstOrNull { deviceId.isNotEmpty() && it.device == deviceId }?.let { mine ->
            return if (mine.out && !mine.connected) Pick.Closed else Pick.Take(mine.id)
        }
        seats.firstOrNull { it.device == null && !it.connected && !it.out }?.let { return Pick.Take(it.id) }
        return if (seats.any { it.out }) Pick.Closed else Pick.Full
    }
}
