package com.cardrop.navi

import com.cardrop.drive.GeoPoint
import com.cardrop.drive.haversineKm

/**
 * Prefer a recent SDK measurement, using its measurement time rather than its callback arrival time.
 * Retain GPS when the SDK is unavailable, stale, invalid, or over 1 km from the last known GPS fix.
 */
internal fun crewLocationFix(sdk: GpsFix?, gps: GpsFix?, nowMillis: Long): GpsFix? {
    val fallback = gps?.takeIf(::validCrewCoordinates)
    val preferred = currentCrewFix(sdk, nowMillis) ?: return fallback
    if (fallback != null && haversineKm(
            GeoPoint(preferred.lat, preferred.lng), GeoPoint(fallback.lat, fallback.lng),
        ) > 1.0
    ) return fallback
    return preferred
}

internal fun currentCrewFix(fix: GpsFix?, nowMillis: Long): GpsFix? = fix?.takeIf {
    validCrewCoordinates(it) && nowMillis - it.timeMillis in 0..5_000
}

private fun validCrewCoordinates(fix: GpsFix): Boolean =
    fix.lat.isFinite() && fix.lng.isFinite() && fix.lat in -90.0..90.0 && fix.lng in -180.0..180.0 &&
        !(fix.lat == 0.0 && fix.lng == 0.0)
