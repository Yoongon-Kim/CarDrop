package com.cardrop.navi

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The crew's own measured vehicle position. It never starts or changes Pleos navigation. */
class PleosFusedLocationSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : LocationSource {
    private val _fix = MutableStateFlow<GpsFix?>(null)
    override val fix: StateFlow<GpsFix?> = _fix
    val supported: Boolean by lazy { runCatching { FusedLocation(context).isSupported() }.getOrDefault(false) }
    private var client: FusedLocation? = null
    private var liveListener: FusedLocationListener? = null
    private var watchdog: Job? = null
    private var requested = false
    private var generation = 0L

    override fun start(): Boolean {
        requested = true
        if (client != null) return true
        if (!supported || context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        val session = ++generation
        var receivedLive = false
        fun receive(data: LocationData?, cached: Boolean) {
            scope.launch {
                if (generation != session) return@launch
                val measured = currentCrewFix(data?.toCrewGpsFix(), now())
                if (measured != null) {
                    val previous = _fix.value
                    // A delayed cache reply must not replace a newer stream measurement.
                    if (previous == null || measured.timeMillis >= previous.timeMillis) _fix.value = measured
                    if (!cached && !receivedLive) {
                        receivedLive = true
                        Log.i(TAG, "FusedLocation live position received; measurement age=${now() - measured.timeMillis}ms")
                    }
                } else if (!cached) {
                    // Null/invalid/stale live data means the diagram should use GPS.
                    _fix.value = null
                }
            }
        }
        fun events(cached: Boolean) = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) = receive(location, cached)
            override fun onError(exception: Exception) {
                scope.launch {
                    if (generation != session) return@launch
                    if (!cached) _fix.value = null
                    Log.w(TAG, "Vehicle location unavailable: ${exception.message}; GPS remains available")
                }
            }
        }
        return try {
            val sdk = FusedLocation(context)
            val live = events(cached = false)
            val cache = events(cached = true)
            client = sdk
            liveListener = live
            sdk.initialize()
            sdk.registerFusedLocationCallback(live)
            Log.i(TAG, "FusedLocation subscription requested")
            watchdog = scope.launch {
                while (isActive && generation == session) {
                    val previous = _fix.value
                    if (previous != null && currentCrewFix(previous, now()) == null) {
                        _fix.value = null
                        Log.w(TAG, "Vehicle position expired; using GPS")
                    }
                    // A cache seed can help on connection/reconnection, but its original timestamp is kept.
                    if (_fix.value == null) runCatching { sdk.getLastKnownLocation(cache) }
                        .onFailure { Log.w(TAG, "Cannot query vehicle position: ${it.message}") }
                    delay(1_000)
                }
            }
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot start vehicle location: ${exception.message}; using GPS")
            stop()
            false
        }
    }

    /** Called after a runtime grant; do not connect outside an active crew. */
    fun onPermissionGranted() {
        if (requested) start()
    }

    override fun stop() {
        requested = false
        generation++
        watchdog?.cancel()
        watchdog = null
        val sdk = client
        client = null
        liveListener?.let { listener -> runCatching { sdk?.unregisterFusedLocationCallback(listener) } }
        liveListener = null
        runCatching { sdk?.release() }
        _fix.value = null
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "CarDropFused"
    }
}

/** Preserve the SDK measurement time: a newly received cached reply can still be hours old. */
internal fun LocationData.toCrewGpsFix(): GpsFix = GpsFix(
    lat = latitude,
    lng = longitude,
    speedKmh = null,
    bearing = heading.toDouble().takeIf { it.isFinite() && it in 0.0..360.0 },
    timeMillis = timestampMillis,
)
