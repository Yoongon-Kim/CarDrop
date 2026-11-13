package com.cardrop.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner: lead. Whether the crew's trip has started.
 *
 * The cars don't start the journey until the crew's host (방장) presses 출발 on board 01. A car that
 * joins before that waits on board 04 ("…가 출발하면 같이 출발해요"); when the host starts, its guidance
 * starts by itself ([TripCoordinator]) and tools/drive_gps.py starts moving its GPS.
 *
 * Real crew: [FirebaseTrip]. Bot crew: [AlwaysStarted], the bots are already on the road.
 */
interface TripStart {
    /** True once the host started the trip of the current crew. */
    val started: StateFlow<Boolean>

    /** The host starts the trip for the whole crew (board 01 출발). */
    fun start()

    /** The bot crew: nobody waits. */
    object AlwaysStarted : TripStart {
        override val started: StateFlow<Boolean> = MutableStateFlow(true)
        override fun start() = Unit
    }
}
