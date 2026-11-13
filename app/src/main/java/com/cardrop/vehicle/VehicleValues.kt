package com.cardrop.vehicle

/** Units from the Vehicle 2.3.2 sources: range = metres, current driving time = minutes. */
internal object VehicleValues {
    fun fuel(percent: Int?): Int? = percent?.takeIf { it in 0..100 }
    fun rangeKm(metres: Float?): Float? = metres?.takeIf { it.isFinite() && it >= 0f }?.div(1_000f)
    fun drivingMinutes(minutes: Int?): Int? = minutes?.takeIf { it >= 0 }
}
