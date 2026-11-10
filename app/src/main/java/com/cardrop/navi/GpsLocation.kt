package com.cardrop.navi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One GPS reading. [speedKmh] and [bearing] are null when the GPS did not report them. */
data class GpsFix(
    val lat: Double,
    val lng: Double,
    val speedKmh: Double?,
    val bearing: Double?,
    val timeMillis: Long,
)

/** Where this car is, from the GPS. */
interface LocationSource {
    val fix: StateFlow<GpsFix?>

    /** Starts GPS updates (once a second). Returns false while the location permission is missing. */
    fun start(): Boolean

    fun stop()
}

/**
 * Owner: Member B.
 *
 * Android's GPS, the same source the built-in navigation uses. On the emulator the GPS is fed by
 * `tools/drive_gps.py` (or Extended controls > Location), which is how the demo car "drives".
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    private val manager = context.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<GpsFix?>(null)
    override val fix: StateFlow<GpsFix?> = _fix

    private var started = false
    private val listener = LocationListener { location -> _fix.value = location.toFix() }

    @SuppressLint("MissingPermission") // checked just below
    override fun start(): Boolean {
        if (started) return true
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted || manager == null) return false
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider is off")
            return false
        }
        manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { _fix.value = it.toFix() }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        started = true
        return true
    }

    override fun stop() {
        if (!started) return
        manager?.removeUpdates(listener)
        started = false
    }

    private fun Location.toFix() = GpsFix(
        lat = latitude,
        lng = longitude,
        speedKmh = if (hasSpeed() && speed > 0f) speed * 3.6 else null,
        // The emulator always reports bearing 0 (north); treat exactly 0 as unknown and let
        // GuidanceTracker work the direction out from movement.
        bearing = if (hasBearing() && bearing != 0f && hasSpeed() && speed > 0f) bearing.toDouble() else null,
        timeMillis = time,
    )

    private companion object {
        const val TAG = "CarDropGps"
    }
}
