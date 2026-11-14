package com.cardrop.safety

import kotlin.math.abs

/** Pure Kotlin, driven by measurement time. Restore retains confirmed-event locks, never old derivatives. */
class SafetyDetector(initial: SafetyRecord, startNanos: Long) {
    var record: SafetyRecord = initial
        private set

    /** The same trip under its server-confirmed id and start time (the host's 출발; see [sameTripRestamped]). */
    fun retag(tripId: String, startedAtMillis: Long) {
        record = record.copy(tripId = tripId, startedAtMillis = startedAtMillis)
    }
    private data class Sample(val nanos: Long, val speed: Double)
    private val samples = ArrayDeque<Sample>()
    private var previous: SpeedReading? = null
    private var clockNanos = startNanos
    private var accelSince: Long? = null
    private var brakeSince: Long? = null
    private var accelStartSpeed = 0.0
    private var brakeStartSpeed = 0.0
    private var normalSince: Long? = null
    private var lastAcceptedNanos: Long? = null

    fun tick(nowNanos: Long, wallMillis: Long, paused: Boolean) {
        if (record.status != SafetyTripStatus.RECORDING || nowNanos < clockNanos) return
        val elapsed = (nowNanos - clockNanos) / 1e9
        clockNanos = nowNanos
        record = record.copy(eligibleSeconds = record.eligibleSeconds + if (paused) 0.0 else elapsed,
            updatedAtMillis = wallMillis)
        if (lastAcceptedNanos?.let { nowNanos - it > 350_000_000L } == true) clearContinuity()
    }

    fun ingest(reading: SpeedReading, wallMillis: Long, paused: Boolean = false) {
        if (record.status != SafetyTripStatus.RECORDING) return
        tick(reading.receivedNanos, wallMillis, paused)
        if (!reading.metresPerSecond.isFinite() || reading.metresPerSecond > 100 || reading.receivedNanos - reading.measuredNanos !in 0..1_000_000_000L) return
        if (lastAcceptedNanos?.let { reading.measuredNanos <= it } == true) return
        val before = previous
        val dt = before?.let { (reading.measuredNanos - it.measuredNanos) / 1e9 } ?: 0.0
        lastAcceptedNanos = reading.measuredNanos
        if (paused || reading.metresPerSecond < 0) { clearContinuity(); return }
        if (dt > SafetyRules.MAX_GAP_SECONDS) clearContinuity()
        val continuous = before != null && dt > 0 && dt <= SafetyRules.MAX_GAP_SECONDS && previous != null
        val moving = reading.metresPerSecond * 3.6 >= 10
        if (continuous) {
            record = record.copy(
                observedSeconds = record.observedSeconds + dt,
                movingSeconds = record.movingSeconds + if (moving) dt else 0.0,
                distanceKm = record.distanceKm + (before!!.metresPerSecond + reading.metresPerSecond) * .5 * dt / 1000,
            )
        }
        previous = reading
        samples.addLast(Sample(reading.measuredNanos, reading.metresPerSecond))
        while (samples.isNotEmpty() && reading.measuredNanos - samples.first().nanos > 550_000_000L) samples.removeFirst()
        acceleration()?.let { a -> detectAcceleration(a, reading, wallMillis) }
    }

    private fun acceleration(): Double? {
        if (samples.size < 3 || samples.last().nanos - samples.first().nanos < 400_000_000L) return null
        val origin = samples.first().nanos
        val times = samples.map { (it.nanos - origin) / 1e9 }
        val meanT = times.average()
        val meanV = samples.map { it.speed }.average()
        var numerator = 0.0; var denominator = 0.0
        samples.forEachIndexed { i, s -> numerator += (times[i] - meanT) * (s.speed - meanV); denominator += (times[i] - meanT) * (times[i] - meanT) }
        return if (denominator > 0) numerator / denominator else null
    }

    private fun detectAcceleration(a: Double, reading: SpeedReading, wall: Long) {
        val t = reading.measuredNanos
        if (abs(a) < 1.5) {
            if (normalSince == null) normalSince = t
            if (t - normalSince!! >= 1_000_000_000L) record = record.copy(accelerationLocked = false, brakingLocked = false)
        } else normalSince = null
        if (a >= SafetyRules.ACCELERATION && !record.accelerationLocked) {
            if (accelSince == null) { accelSince = t; accelStartSpeed = samples.first().speed }
            if (t - accelSince!! >= 500_000_000L && (reading.metresPerSecond - accelStartSpeed) * 3.6 >= 5 && reading.metresPerSecond * 3.6 >= 10) {
                append(SafetyEventType.ACCELERATION, wall, 2, peak = a)
                record = record.copy(accelerationLocked = true); accelSince = null
            }
        } else accelSince = null
        if (a <= SafetyRules.BRAKING && !record.brakingLocked) {
            if (brakeSince == null) { brakeSince = t; brakeStartSpeed = samples.first().speed }
            if (t - brakeSince!! >= 500_000_000L && brakeStartSpeed * 3.6 >= 20) {
                append(SafetyEventType.BRAKING, wall, 3, peak = a)
                record = record.copy(brakingLocked = true); brakeSince = null
            }
        } else brakeSince = null
    }

    private fun append(type: SafetyEventType, wall: Long, penalty: Int, peak: Double = 0.0) {
        val event = SafetyEvent("${record.id}:${record.revision + 1}", type, wall, penalty, peak)
        record = record.copy(events = record.events + event, revision = record.revision + 1)
    }

    private fun clearContinuity() {
        previous = null; samples.clear(); accelSince = null; brakeSince = null; normalSince = null
    }

    fun finish(wallMillis: Long, completed: Boolean) {
        if (record.status != SafetyTripStatus.RECORDING) return
        record = record.copy(status = if (completed) SafetyTripStatus.COMPLETED else SafetyTripStatus.STOPPED,
            endedAtMillis = wallMillis, updatedAtMillis = wallMillis, revision = record.revision + 1)
        clearContinuity()
    }
}

/**
 * True when [key] (the trip being recorded) and the new start belong to one trip: the same crew [code], and a start
 * time that moved by less than a minute. Firebase first reports the host's own 출발 with its local guess of the
 * server time, then corrects it to the server's value; a crew starts only once.
 */
internal fun sameTripRestamped(key: String?, code: String, recordedStart: Long, newStart: Long): Boolean =
    key != null && key.startsWith("$code-") && kotlin.math.abs(newStart - recordedStart) < 60_000
