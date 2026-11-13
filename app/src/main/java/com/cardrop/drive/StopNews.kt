package com.cardrop.drive

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.Place
import com.cardrop.core.text.particle
import com.cardrop.navi.WaypointResult

/**
 * Owner: Member B.
 *
 * The crew message a car sends when it stops at the crew's stop or drives past it ([TripCoordinator]),
 * and reading those messages back for board 14 "도착 예정". Each car's own navigator decides which
 * ([com.cardrop.navi.GuidanceTracker]), so these messages are the crew's shared record of who stopped.
 * The sender and the reader build the text with the same functions, so a message is recognised exactly.
 */
object StopNews {
    fun arrived(place: Place): String = "${place.name}에 도착했어요"

    fun passed(place: Place): String = "${place.name.particle("을", "를")} 지나쳤어요"

    /**
     * What each car (by id) last said about [place] in crew messages sent at or after [sinceMillis]
     * (the vote's time, so an earlier visit to the same place doesn't count).
     */
    fun results(drops: List<Drop>, place: Place, sinceMillis: Long): Map<String, WaypointResult> {
        val arrived = arrived(place)
        val passed = passed(place)
        return drops
            .filter { it.kind == DropKind.SOCIAL && it.createdAtMillis >= sinceMillis }
            .sortedBy { it.createdAtMillis }
            .mapNotNull { drop ->
                when (drop.title) {
                    arrived -> drop.fromCarId to WaypointResult.ARRIVED
                    passed -> drop.fromCarId to WaypointResult.PASSED
                    else -> null
                }
            }
            .toMap() // a later message replaces an earlier one
    }
}
