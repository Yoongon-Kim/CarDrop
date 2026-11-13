package com.cardrop.drive

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import kotlinx.coroutines.flow.StateFlow

/**
 * Owner: Member B.
 *
 * Positions of every crew car. The Vehicle SDK has no location or speed API, so they come from GPS:
 * [com.cardrop.navi.GpsCrewPositions] (on the emulator, `tools/drive_gps.py` plays each car's GPS).
 */
interface RouteSimulator {
    /** carId -> position */
    val positions: StateFlow<Map<String, CarPosition>>

    fun start(carIds: List<String>, destination: Place)

    fun stop()
}
