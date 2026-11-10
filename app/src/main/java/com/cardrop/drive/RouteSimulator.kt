package com.cardrop.drive

import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owner: Member B.
 *
 * Positions of every crew car. The Vehicle SDK has no location or speed API, so for the demo
 * the cars drive the real Seoul -> Gangneung road route in [DemoRoute]. A real version could use
 * Android location for this car and sync the others through CrewRepository.
 */
interface RouteSimulator {
    /** carId -> position */
    val positions: StateFlow<Map<String, CarPosition>>

    fun start(carIds: List<String>, destination: Place)

    fun stop()
}

/**
 * Moves cars along [DemoRoute] at 100 km/h, sped up [speedUp] times so the demo shows progress.
 * At 6x a car covers 10 km a minute: a 2-minute vote ends before the cars reach the stop it picked.
 *
 * Cars start 3 km apart on the 영동고속도로 so ETAs differ, as in board 04.
 * A car stands still while [isParked] says so (gear P), e.g. for the quiz game (board 15).
 * Calling [start] again (new destination) keeps each car where it is.
 */
class ScriptedRouteSimulator(
    private val scope: CoroutineScope,
    private val isParked: (carId: String) -> Boolean = { false },
    private val speedUp: Int = 6,
) : RouteSimulator {

    private val _positions = MutableStateFlow<Map<String, CarPosition>>(emptyMap())
    override val positions: StateFlow<Map<String, CarPosition>> = _positions

    private var job: Job? = null

    /** carId -> km driven from the start of [DemoRoute]. */
    private val driven = mutableMapOf<String, Double>()

    override fun start(carIds: List<String>, destination: Place) {
        job?.cancel()
        driven.keys.retainAll(carIds.toSet())
        carIds.forEachIndexed { i, id -> driven.getOrPut(id) { (START_KM - i * 3.0).coerceAtLeast(0.0) } }
        val kmPerTick = 100.0 / 3600.0 * speedUp // 100 km/h, one tick per real second
        job = scope.launch {
            while (isActive) {
                _positions.value = carIds.associateWith { id -> positionAt(id, driven.getValue(id)) }
                delay(1_000)
                carIds.forEach { id ->
                    if (!isParked(id)) driven[id] = (driven.getValue(id) + kmPerTick).coerceAtMost(DemoRoute.totalKm)
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        driven.clear()
        _positions.value = emptyMap()
    }

    private fun positionAt(carId: String, km: Double): CarPosition {
        val point = DemoRoute.pointAt(km)
        val remaining = (DemoRoute.totalKm - km).toFloat()
        return CarPosition(
            carId = carId,
            lat = point.lat,
            lng = point.lng,
            kmToDestination = remaining,
            etaMinutes = (remaining / 85f * 60f).toInt(), // ~85 km/h average with traffic
        )
    }

    private companion object {
        /** Just after 신갈JC, on the 영동고속도로 heading east. */
        const val START_KM = 40.0
    }
}
