package com.cardrop.drive

import com.cardrop.core.model.Gear
import com.cardrop.core.model.Place
import com.cardrop.navi.GpsFix

/** At or below this the car warns once and opens board 05 (FuelWarningTracker). */
const val LOW_FUEL_PERCENT = 15

/** Below this the car's crew card on board 04 turns orange ("연료 경고"), as in the design (`c[3] < 20`, target 644). */
const val LOW_FUEL_CARD_PERCENT = 20

/** Rearm after refuelling, so 15/16% jitter and screen changes cannot repeat the warning. */
class FuelWarningTracker {
    private var warned = false
    fun update(percent: Int?): Boolean {
        if (percent == null || percent !in 0..100) return false
        if (percent >= 20) warned = false
        if (percent > LOW_FUEL_PERCENT || warned) return false
        warned = true
        return true
    }
    fun reset() { warned = false }
}

/**
 * The pill on boards 04, 05, 06 and 14. It doesn't limit touch: a passenger may use the screen while
 * driving. Only the games (board 15) are closed while driving ([gamesOpen], `ai/game/OmokScreen.kt`).
 */
enum class DrivingInput(val label: String) {
    PARKED("P · 정차 중"), MOVING("주행 중 · 음성 모드"), UNKNOWN("차량 상태 확인 중")
}

fun drivingInput(gear: Gear, fix: GpsFix?, now: Long): DrivingInput = when (gear) {
    Gear.PARK -> DrivingInput.PARKED
    Gear.DRIVE, Gear.REVERSE, Gear.NEUTRAL -> DrivingInput.MOVING
    Gear.UNKNOWN -> if (fix != null && now - fix.timeMillis in 0..10_000 &&
        fix.speedKmh?.let { it.isFinite() && it > 0 } == true) DrivingInput.MOVING else DrivingInput.UNKNOWN
}

/** Above this GPS speed the car counts as moving for the games, whatever the gear says. */
const val GAMES_MAX_SPEED_KMH = 5.0

/**
 * True when board 15 (the games) may open: the gear is P, and the GPS doesn't say the car is moving.
 * The gear alone isn't enough on the emulator: the fake gear is a DEMO-panel button, while `tools/drive_gps.py`
 * drives the car at 100 km/h. A GPS reading older than 10 s (or none, or one without a speed) can't close the
 * games: a parked car indoors has no fix. Any other gear, or an unknown one, keeps them closed.
 */
fun gamesOpen(gear: Gear, fix: GpsFix?, now: Long): Boolean {
    if (gear != Gear.PARK) return false
    val fresh = fix != null && now - fix.timeMillis in 0..10_000
    val moving = fresh && fix?.speedKmh?.let { it.isFinite() && it > GAMES_MAX_SPEED_KMH } == true
    return !moving
}

/** Route distance plus the offset to the station and a 5 km reserve. */
fun reachableFuelStops(pool: List<Place>, geometry: RouteGeometry, progressKm: Double, rangeKm: Float?, minimumAheadKm: Double = 0.5): List<Place> {
    if (rangeKm == null || !rangeKm.isFinite() || rangeKm <= 5f) return emptyList()
    return pool.filter { "주유" in it.detail }.mapNotNull { place ->
        val point = geometry.locate(place.lat, place.lng)
        val aheadKm = point.km - progressKm
        if (point.offMeters <= 1_500 && aheadKm > minimumAheadKm && aheadKm + point.offMeters / 1_000 + 5 <= rangeKm)
            place to aheadKm else null
    }.sortedBy { it.second }.map { it.first }
}

enum class DriveVoiceCommand { SHARE_FUEL, ALONE, JOIN, PASS, START, SKIP }

/** Ambiguous recognition must never send a drop or change a route. */
fun driveVoiceCommand(text: String?): DriveVoiceCommand? {
    val value = text?.replace(Regex("[\\s,.!?。]"), "") ?: return null
    return when (value) {
        "응", "알려줘", "응알려줘", "같이주유하자" -> DriveVoiceCommand.SHARE_FUEL
        "혼자갈게", "혼자갈게요", "아니혼자갈게" -> DriveVoiceCommand.ALONE
        "같이갈래요", "같이갈게", "같이갈게요", "같이가자", "같이가요" -> DriveVoiceCommand.JOIN
        "가던길갈래요", "그냥갈게요" -> DriveVoiceCommand.PASS
        "안내시작", "지금안내시작", "출발" -> DriveVoiceCommand.START
        "안들르기", "우리차는안들르기", "안들를게요" -> DriveVoiceCommand.SKIP
        else -> null
    }
}
